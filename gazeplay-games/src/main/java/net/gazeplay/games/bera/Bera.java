package net.gazeplay.games.bera;

import javafx.geometry.Dimension2D;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import lombok.extern.slf4j.Slf4j;
import net.gazeplay.GameLifeCycle;
import net.gazeplay.IGameContext;
import net.gazeplay.commons.configuration.Configuration;
import net.gazeplay.commons.gamevariants.difficulty.Difficulty;
import net.gazeplay.commons.gamevariants.difficulty.SourceSet;
import net.gazeplay.commons.random.ReplayablePseudoRandom;
import net.gazeplay.commons.utils.games.ResourceFileManager;
import net.gazeplay.commons.utils.multilinguism.Multilinguism;
import net.gazeplay.commons.utils.multilinguism.MultilinguismFactory;
import net.gazeplay.commons.utils.stats.Stats;
import net.gazeplay.commons.utils.stats.TargetAOI;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Bera implements GameLifeCycle{

    private static final int NBMAXPICTO = 10;
    private static final double MAXSIZEPICTO = 250;

    private Text questionText;
    private final String directoryRessource = "data/bera";

    private final int nbLines = 1;
    private final int nbColumns = 2;
    private final boolean fourThree;

    private int rightDecision = 0;
    private int wrongDecision = 0;
    private boolean firstWrong = false;
    private int startIndex = 0;

    private final IGameContext gameContext;
    private final Stats stats;
    private RoundDetails currentRoundDetails;
    private final ReplayablePseudoRandom randomGenerator;

    private final ArrayList<TargetAOI> targetAOIList;

    public Bera(final boolean fourThree, final IGameContext gameContext, final Stats stats) {
        this.gameContext = gameContext;
        this.fourThree = fourThree;
        this.stats = stats;
        this.targetAOIList = new ArrayList<>();
        this.gameContext.startScoreLimiter();
        this.gameContext.startTimeLimiter();
        this.randomGenerator = new ReplayablePseudoRandom();
        this.stats.setGameSeed(randomGenerator.getSeed());
    }

    public Bera(final boolean fourThree, final IGameContext gameContext, final Stats stats, double gameSeed) {
        this.gameContext = gameContext;
        this.fourThree = fourThree;
        this.stats = stats;
        this.targetAOIList = new ArrayList<>();
        this.gameContext.startScoreLimiter();
        this.gameContext.startTimeLimiter();
        this.randomGenerator = new ReplayablePseudoRandom(gameSeed);
    }

    @Override
    public void launch() {
        gameContext.setLimiterAvailable();

        final int numberOfImagesToDisplayPerRound = nbLines * nbColumns;
        log.debug("numberOfImagesToDisplayPerRound = {}", numberOfImagesToDisplayPerRound);

        final int winnerImageIndexAmongDisplayedImages = 0;
        log.debug("winnerImageIndexAmongDisplayedImages = {}", winnerImageIndexAmongDisplayedImages);

        currentRoundDetails = pickAndBuildRandomPictures(numberOfImagesToDisplayPerRound, randomGenerator, winnerImageIndexAmongDisplayedImages);

        stats.notifyNewRoundReady();
        gameContext.getGazeDeviceManager().addStats(stats);
        gameContext.firstStart();

        this.startGame();
    }

    public void startGame(){
        final List<Rectangle> pictogramesList = new ArrayList<>(20); // storage of actual Pictogramm nodes in order to delete

        final List<Image> listOfPictos = currentRoundDetails.getPictos();

        if (listOfPictos != null && !listOfPictos.isEmpty() && listOfPictos.size() <= NBMAXPICTO) {

            final Dimension2D screenDimension = gameContext.getCurrentScreenDimensionSupplier().get();
            final double screenWidth = screenDimension.getWidth();

            final double nbPicto = listOfPictos.size();

            double pictoSize = screenWidth / (nbPicto + 1);

            log.debug("screenWidth/(nbPicto) : {}", pictoSize);

            pictoSize = Math.min(pictoSize, MAXSIZEPICTO);

            log.debug("Picto Size: {}", pictoSize);

            int i = 0;
            final double shift = screenWidth / 2 - ((nbPicto / 2) * pictoSize * 1.1);

            log.debug("shift Size: {}", shift);

            final Dimension2D gamePaneDimension2D = gameContext.getGamePanelDimensionProvider().getDimension2D();
            final double positionY = gamePaneDimension2D.getHeight() / 2 - questionText.getBoundsInParent().getHeight() / 2;

            for (final Image picto : listOfPictos) {

                final Rectangle pictoRectangle = new Rectangle(pictoSize, pictoSize);
                pictoRectangle.setFill(new ImagePattern(picto));
                pictoRectangle.setY(positionY + 100);
                pictoRectangle.setX(shift + (i++ * pictoSize * 1.1));
                pictogramesList.add(pictoRectangle);
            }

            gameContext.getChildren().addAll(pictogramesList);
        }

        gameContext.getChildren().removeAll(pictogramesList);

        //log.debug("Adding {} pictures", currentRoundDetails.getPictureCardList().size());
        if(currentRoundDetails != null) {
            gameContext.getChildren().addAll(currentRoundDetails.getPictureCardList());

            for (final net.gazeplay.games.bera.PictureCard p : currentRoundDetails.getPictureCardList()) {
                //  log.debug("p = {}", p);
                p.toFront();
                p.setOpacity(1);
            }
        }

        stats.notifyNewRoundReady();

        gameContext.onGameStarted(2000);
    }

    public void checkAllPictureCardChecked(){
        boolean check = true;
        for (final net.gazeplay.games.bera.PictureCard p : currentRoundDetails.getPictureCardList()) {
            if (!p.isAlreadySee()) {
                check = false;
            }
        }
        if (check){
            for (final net.gazeplay.games.bera.PictureCard p : currentRoundDetails.getPictureCardList()) {
                p.setVisibleProgressIndicator();
            }
        }
    }

    /**
     * this method should be called when exiting the game, or before starting a new round, in order to clean up all
     * resources in both UI and memory
     */
    @Override
    public void dispose() {
        if (currentRoundDetails != null) {
            if (currentRoundDetails.getPictureCardList() != null) {
                gameContext.getChildren().removeAll(currentRoundDetails.getPictureCardList());
            }
            currentRoundDetails = null;
        }
        stats.setTargetAOIList(targetAOIList);
    }

    void removeAllIncorrectPictureCards() {
        //set the target AOI end time for this round

        final long endTime = System.currentTimeMillis();
        final int numberOfImagesToDisplayPerRound = nbLines * nbColumns;

        for (int i = 1; i <= numberOfImagesToDisplayPerRound; i++) {
            targetAOIList.get(targetAOIList.size() - i).setTimeEnded(endTime);
        }

        if (this.currentRoundDetails == null) {
            return;
        }

        // Collect all items to be removed from the User Interface
        final List<net.gazeplay.games.bera.PictureCard> pictureCardsToHide = new ArrayList<>();
        for (final net.gazeplay.games.bera.PictureCard pictureCard : this.currentRoundDetails.getPictureCardList()) {
            if (!pictureCard.isWinner()) {
                pictureCardsToHide.add(pictureCard);
            }
        }
        // remove all at once, in order to update the UserInterface only once
        gameContext.getChildren().removeAll(pictureCardsToHide);
    }

    net.gazeplay.games.bera.RoundDetails pickAndBuildRandomPictures(final int numberOfImagesToDisplayPerRound, final ReplayablePseudoRandom random,
                                                                         final int winnerImageIndexAmongDisplayedImages) {

        final Configuration config = gameContext.getConfiguration();

        int directoriesCount;
        final String directoryName;
        List<String> resourcesFolders = new LinkedList<>();

        final String resourcesDirectory = this.directoryRessource;
        final String imagesDirectory = resourcesDirectory + "/wordsVersion1/";
        directoryName = imagesDirectory;

        // Here we filter out any unwanted resource folders, based on the difficulty JSON file
        Set<String> difficultySet;
        try {
            SourceSet sourceSet = new SourceSet(resourcesDirectory + "/difficulties.json");
            difficultySet = (sourceSet.getResources(Difficulty.NORMAL));
        } catch (FileNotFoundException fe) {
            log.info("No difficulty file found; Reading from all directories");
            difficultySet = Collections.emptySet();
        }

        Set<String> tempResourcesFolders = ResourceFileManager.getResourceFolders(imagesDirectory);

        // If nothing can be found we take the entire folder contents.
        if (!difficultySet.isEmpty()) {
            Set<String> finalDifficultySet = difficultySet;
            tempResourcesFolders = tempResourcesFolders
                .parallelStream()
                .filter(s -> finalDifficultySet.parallelStream().anyMatch(s::contains))
                .collect(Collectors.toSet());
        }

        resourcesFolders.addAll(tempResourcesFolders);

        directoriesCount = resourcesFolders.size();

        final String language = config.getLanguage();

        if (directoriesCount == 0) {
            log.warn("No images found in Directory " + directoryName);
            error(language);
            return null;
        }

        int posX = 0;
        int posY = 0;

        final GameSizing gameSizing = new GameSizingComputer(nbLines, nbColumns, fourThree)
            .computeGameSizing(gameContext.getGamePanelDimensionProvider().getDimension2D());

        final List<net.gazeplay.games.bera.PictureCard> pictureCardList = new ArrayList<>();
        String questionSoundPath = null;
        String question = null;
        List<Image> pictograms = null;

        for (int i = 0; i < numberOfImagesToDisplayPerRound; i++) {

            final String folder = resourcesFolders.remove(this.startIndex);

            final Set<String> files = ResourceFileManager.getResourcePaths(folder);

            final String randomImageFile = (String) files.toArray()[i];

            final net.gazeplay.games.bera.PictureCard pictureCard = new net.gazeplay.games.bera.PictureCard(gameSizing.width * posX + gameSizing.shift,
                gameSizing.height * posY, gameSizing.width, gameSizing.height, gameContext,
                winnerImageIndexAmongDisplayedImages == i, randomImageFile + "", stats, this);

            pictureCardList.add(pictureCard);

            final TargetAOI targetAOI = new TargetAOI(gameSizing.width * (posX + 0.25), gameSizing.height * (posY + 1), (int) gameSizing.height,
                System.currentTimeMillis());
            targetAOIList.add(targetAOI);


            if ((i + 1) % nbColumns != 0) {
                posX++;
            } else {
                posY++;
                posX = 0;
            }
        }

        Collections.shuffle(pictureCardList);

        return new net.gazeplay.games.bera.RoundDetails(pictureCardList, winnerImageIndexAmongDisplayedImages, questionSoundPath, question,
            pictograms);
    }

    private void error(final String language) {

        gameContext.clear();
        // HomeUtils.home(scene, group, choiceBox, null);

        final Multilinguism multilinguism = MultilinguismFactory.getSingleton();

        final Text error = new Text(multilinguism.getTranslation("WII-error", language));
        final Region root = gameContext.getRoot();
        error.setX(root.getWidth() / 2. - 100);
        error.setY(root.getHeight() / 2.);
        error.setId("item");
        gameContext.getChildren().addAll(error);
    }

    public void indexFile() {
        startIndex++;
    }
}
