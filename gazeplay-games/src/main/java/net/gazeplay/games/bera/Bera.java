package net.gazeplay.games.bera;

import javafx.event.EventHandler;
import javafx.geometry.Dimension2D;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
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

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Bera implements GameLifeCycle {

    private static final int NBMAXPICTO = 10;
    private static final double MAXSIZEPICTO = 250;
    private final String directoryRessource = "data/bera";
    private final int nbLines = 1;
    private final int nbColumns = 2;
    private final boolean fourThree;
    private final IGameContext gameContext;
    private final Stats stats;
    private final ReplayablePseudoRandom randomGenerator;
    private final ArrayList<TargetAOI> targetAOIList;
    public int indexFileImage = 0;
    public CustomInputEventHandlerKeyboard customInputEventHandlerKeyboard = new CustomInputEventHandlerKeyboard();
    private Text questionText;
    //Phonology
    private int totalPhonology = 0;
    private int simpleScoreItemsPhonology = 0;
    private int complexScoreItemsPhonology = 0;
    private int scoreLeftTargetItemsPhonology = 0;
    private int scoreRightTargetItemsPhonology = 0;
    //Semantics
    private int totalSemantic = 0;
    private int simpleScoreItemsSemantic = 0;
    private int complexScoreItemsSemantic = 0;
    private int frequentScoreItemSemantic = 0;
    private int infrequentScoreItemSemantic = 0;
    private int scoreLeftTargetItemsSemantic = 0;
    private int scoreRightTargetItemsSemantic = 0;
    //Word comprehension
    private int totalWordComprehension = 0;
    private int totalItemsAddedManually = 0;
    private int total = 0;
    private RoundDetails currentRoundDetails;
    private boolean canRemoveItemManually = true;

    public Bera(final boolean fourThree, final IGameContext gameContext, final Stats stats) {
        this.gameContext = gameContext;
        this.fourThree = fourThree;
        this.stats = stats;
        this.targetAOIList = new ArrayList<>();
        this.gameContext.startScoreLimiter();
        this.gameContext.startTimeLimiter();
        this.randomGenerator = new ReplayablePseudoRandom();
        this.stats.setGameSeed(randomGenerator.getSeed());

        this.gameContext.getPrimaryScene().addEventFilter(KeyEvent.KEY_PRESSED, customInputEventHandlerKeyboard);
    }

    public Bera(final boolean fourThree, final IGameContext gameContext, final Stats stats, double gameSeed) {
        this.gameContext = gameContext;
        this.fourThree = fourThree;
        this.stats = stats;
        this.targetAOIList = new ArrayList<>();
        this.gameContext.startScoreLimiter();
        this.gameContext.startTimeLimiter();
        this.randomGenerator = new ReplayablePseudoRandom(gameSeed);

        this.gameContext.getPrimaryScene().addEventFilter(KeyEvent.KEY_PRESSED, customInputEventHandlerKeyboard);
    }

    @Override
    public void launch() {

        this.canRemoveItemManually = true;

        gameContext.setLimiterAvailable();

        final int numberOfImagesToDisplayPerRound = nbLines * nbColumns;
        log.debug("numberOfImagesToDisplayPerRound = {}", numberOfImagesToDisplayPerRound);

        final int winnerImageIndexAmongDisplayedImages = 0;
        log.debug("winnerImageIndexAmongDisplayedImages = {}", winnerImageIndexAmongDisplayedImages);

        currentRoundDetails = pickAndBuildRandomPictures(numberOfImagesToDisplayPerRound, winnerImageIndexAmongDisplayedImages);

        stats.notifyNewRoundReady();
        gameContext.getGazeDeviceManager().addStats(stats);
        gameContext.firstStart();

        this.startGame();
    }

    public void startGame() {
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
        if (currentRoundDetails != null) {
            gameContext.getChildren().addAll(currentRoundDetails.getPictureCardList());

            for (final net.gazeplay.games.bera.PictureCard p : currentRoundDetails.getPictureCardList()) {
                //  log.debug("p = {}", p);
                p.toFront();
                p.setOpacity(1);
            }
        }

        stats.notifyNewRoundReady();

        gameContext.onGameStarted(2000);

        customInputEventHandlerKeyboard.ignoreAnyInput = false;
    }

    public void checkAllPictureCardChecked() {
        boolean check = true;
        for (final net.gazeplay.games.bera.PictureCard p : currentRoundDetails.getPictureCardList()) {
            if (!p.isAlreadySee()) {
                check = false;
            }
        }
        if (check) {
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

    net.gazeplay.games.bera.RoundDetails pickAndBuildRandomPictures(final int numberOfImagesToDisplayPerRound, final int winnerImageIndexAmongDisplayedImages) {

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
        Collections.sort(resourcesFolders);

        directoriesCount = resourcesFolders.size();

        final String language = config.getLanguage();

        if (directoriesCount == 0) {
            log.warn("No images found in Directory " + directoryName);
            error(language);
            return null;
        }

        int posX = 0;
        int posY = 0;

        boolean winnerP1;
        boolean winnerP2;

        String imageP1 = "";
        String imageP2 = "";

        final GameSizing gameSizing = new GameSizingComputer(nbLines, nbColumns, fourThree)
            .computeGameSizing(gameContext.getGamePanelDimensionProvider().getDimension2D());

        final List<net.gazeplay.games.bera.PictureCard> pictureCardList = new ArrayList<>();
        String questionSoundPath = null;
        String question = null;
        List<Image> pictograms = null;

        final String folder = resourcesFolders.remove(this.indexFileImage);

        final Set<String> files = ResourceFileManager.getResourcePaths(folder);

        String randomImageFile1 = (String) files.toArray()[0];
        String randomImageFile2 = (String) files.toArray()[1];

        if (randomImageFile1.contains("First")) {
            imageP1 = randomImageFile1;
            imageP2 = randomImageFile2;
        } else {
            imageP1 = randomImageFile2;
            imageP2 = randomImageFile1;
        }

        if (imageP1.contains("Correct")) {
            winnerP1 = true;
            winnerP2 = false;
        } else {
            winnerP1 = false;
            winnerP2 = true;
        }

        final net.gazeplay.games.bera.PictureCard pictureCard1 = new net.gazeplay.games.bera.PictureCard(
            gameSizing.width * posX + gameSizing.shift,
            gameSizing.height * posY, gameSizing.width, gameSizing.height, gameContext,
            winnerP1, imageP1 + "", stats, this);

        pictureCardList.add(pictureCard1);

        final TargetAOI targetAOI1 = new TargetAOI(
            gameSizing.width * (posX + 0.25),
            gameSizing.height * (posY + 1),
            (int) gameSizing.height,
            System.currentTimeMillis());

        targetAOIList.add(targetAOI1);

        posX++;

        final net.gazeplay.games.bera.PictureCard pictureCard2 = new net.gazeplay.games.bera.PictureCard(
            gameSizing.width * posX + gameSizing.shift,
            gameSizing.height * posY, gameSizing.width, gameSizing.height, gameContext,
            winnerP2, imageP2 + "", stats, this);

        pictureCardList.add(pictureCard2);

        final TargetAOI targetAOI2 = new TargetAOI(
            gameSizing.width * (posX + 0.25),
            gameSizing.height * (posY + 1),
            (int) gameSizing.height,
            System.currentTimeMillis());

        targetAOIList.add(targetAOI2);

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

    public void increaseIndexFileImage(boolean correctAnswer) {
        this.calculateStats(this.indexFileImage, correctAnswer);
        this.indexFileImage = this.indexFileImage + 1;

    }

    private void calculateStats(int index, boolean correctAnswer) {
        if (correctAnswer && !customInputEventHandlerKeyboard.ignoreAnyInput) {
            switch (index) {

                case 0:
                    this.totalPhonology += 1;
                    this.complexScoreItemsPhonology += 1;
                    this.scoreLeftTargetItemsPhonology += 1;
                    break;

                case 1:
                    this.totalPhonology += 1;
                    this.complexScoreItemsPhonology += 1;
                    this.scoreRightTargetItemsPhonology += 1;
                    break;

                case 2:
                    this.totalSemantic += 1;
                    this.simpleScoreItemsSemantic += 1;
                    this.frequentScoreItemSemantic += 1;
                    this.scoreLeftTargetItemsSemantic += 1;
                    break;

                case 3:
                    this.totalPhonology += 1;
                    this.simpleScoreItemsPhonology += 1;
                    this.scoreLeftTargetItemsPhonology += 1;
                    break;

                case 4:
                    this.totalSemantic += 1;
                    this.complexScoreItemsSemantic += 1;
                    this.frequentScoreItemSemantic += 1;
                    this.scoreRightTargetItemsSemantic += 1;
                    break;

                case 5:
                    this.totalSemantic += 1;
                    this.simpleScoreItemsSemantic += 1;
                    this.frequentScoreItemSemantic += 1;
                    this.scoreLeftTargetItemsSemantic += 1;
                    break;

                case 6:
                    this.totalSemantic += 1;
                    this.simpleScoreItemsSemantic += 1;
                    this.infrequentScoreItemSemantic += 1;
                    this.scoreLeftTargetItemsSemantic += 1;
                    break;

                case 7:
                    this.totalPhonology += 1;
                    this.complexScoreItemsPhonology += 1;
                    this.scoreRightTargetItemsPhonology += 1;
                    break;

                case 8:
                    this.totalSemantic += 1;
                    this.complexScoreItemsSemantic += 1;
                    this.frequentScoreItemSemantic += 1;
                    this.scoreLeftTargetItemsSemantic += 1;
                    break;

                case 9:
                    this.totalSemantic += 1;
                    this.simpleScoreItemsSemantic += 1;
                    this.frequentScoreItemSemantic += 1;
                    this.scoreRightTargetItemsSemantic += 1;
                    break;

                case 10:
                    this.totalSemantic += 1;
                    this.complexScoreItemsSemantic += 1;
                    this.infrequentScoreItemSemantic += 1;
                    this.scoreRightTargetItemsSemantic += 1;
                    break;

                case 11:
                    this.totalPhonology += 1;
                    this.complexScoreItemsPhonology += 1;
                    this.scoreRightTargetItemsPhonology += 1;
                    break;

                case 12:
                    this.totalPhonology += 1;
                    this.complexScoreItemsPhonology += 1;
                    this.scoreLeftTargetItemsPhonology += 1;
                    break;

                case 13:
                    this.totalPhonology += 1;
                    this.simpleScoreItemsPhonology += 1;
                    this.scoreRightTargetItemsPhonology += 1;
                    break;

                case 14:
                    this.totalSemantic += 1;
                    this.complexScoreItemsSemantic += 1;
                    this.infrequentScoreItemSemantic += 1;
                    this.scoreRightTargetItemsSemantic += 1;
                    break;

                case 15:
                    this.totalPhonology += 1;
                    this.simpleScoreItemsPhonology += 1;
                    this.scoreRightTargetItemsPhonology += 1;
                    break;

                case 16:
                    this.totalPhonology += 1;
                    this.simpleScoreItemsPhonology += 1;
                    this.scoreLeftTargetItemsPhonology += 1;
                    break;

                case 17:
                    this.totalSemantic += 1;
                    this.simpleScoreItemsSemantic += 1;
                    this.infrequentScoreItemSemantic += 1;
                    this.scoreLeftTargetItemsSemantic += 1;
                    break;

                case 18:
                    this.totalSemantic += 1;
                    this.complexScoreItemsSemantic += 1;
                    this.infrequentScoreItemSemantic += 1;
                    this.scoreRightTargetItemsSemantic += 1;
                    break;

                case 19:
                    this.totalPhonology += 1;
                    this.simpleScoreItemsPhonology += 1;
                    this.scoreLeftTargetItemsPhonology += 1;
                    break;
            }
        }
    }

    private void next(boolean value) {
        if (value) {
            this.totalItemsAddedManually += 1;
            currentRoundDetails.getPictureCardList().get(0).onCorrectCardSelected();
        } else {
            currentRoundDetails.getPictureCardList().get(0).onWrongCardSelected();
        }

    }

    private void removeItemAddedManually() {
        if (this.totalItemsAddedManually != 0 && this.canRemoveItemManually) {
            this.totalItemsAddedManually -= 1;
            this.canRemoveItemManually = false;
        }
    }

    public void finalStats() {
        this.totalWordComprehension = this.scoreLeftTargetItemsPhonology +
            this.scoreRightTargetItemsPhonology +
            this.scoreLeftTargetItemsSemantic +
            this.scoreRightTargetItemsSemantic;
        this.total = this.totalWordComprehension + this.totalItemsAddedManually;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH-mm-ss");
        Date date = new Date();

        FileWriter statsFile;
        try {
            statsFile = new FileWriter("gazeplay-games/src/main/resources/data/bera/statsBera" + "_" + dateFormat.format(date) + "_" + timeFormat.format(date) + ".csv");
            statsFile.append("PHONOLOGIE \n");
            statsFile.append(" - Total Phonologie : ").append(String.valueOf(this.totalPhonology)).append("/10 \n");
            statsFile.append(" - Score items simples : ").append(String.valueOf(this.simpleScoreItemsPhonology)).append("/5 \n");
            statsFile.append(" - Score items complexes : ").append(String.valueOf(this.complexScoreItemsPhonology)).append("/5 \n");
            statsFile.append(" - Score items cibles gauche : ").append(String.valueOf(this.scoreLeftTargetItemsPhonology)).append("/5 \n");
            statsFile.append(" - Score items cibles droite : ").append(String.valueOf(this.scoreRightTargetItemsPhonology)).append("/5 \n");
            statsFile.append("\n");
            statsFile.append("SEMANTIQUE \n");
            statsFile.append(" - Total Semantic : ").append(String.valueOf(this.totalSemantic)).append("/10 \n");
            statsFile.append(" - Score items simples : ").append(String.valueOf(this.simpleScoreItemsSemantic)).append("/5 \n");
            statsFile.append(" - Score items complexes : ").append(String.valueOf(this.complexScoreItemsSemantic)).append("/5 \n");
            statsFile.append(" - Score items fréquents (F+) : ").append(String.valueOf(this.frequentScoreItemSemantic)).append("/5 \n");
            statsFile.append(" - Score items peu fréquents (F-) : ").append(String.valueOf(this.infrequentScoreItemSemantic)).append("/5 \n");
            statsFile.append(" - Score items cibles gauche : ").append(String.valueOf(this.scoreLeftTargetItemsSemantic)).append("/5 \n");
            statsFile.append(" - Score items cibles droite : ").append(String.valueOf(this.scoreRightTargetItemsSemantic)).append("/5 \n");
            statsFile.append("\n");
            statsFile.append("COMPREHENSION DE MOTS \n");
            statsFile.append(" - Total compréhension de mots : ").append(String.valueOf(this.totalWordComprehension)).append("/20 \n");
            statsFile.append(" - Total items ajoutés manuellement : ").append(String.valueOf(this.totalItemsAddedManually)).append("/20 \n");
            statsFile.append(" - Total compréhension de mots avec items sélectionnés manuellement: ").append(String.valueOf(this.total)).append("/20 \n");
            statsFile.close();
        } catch (Exception e) {
            log.info("Error creation csv for Bera stats game !");
            e.printStackTrace();
        }
    }

    private class CustomInputEventHandlerKeyboard implements EventHandler<KeyEvent> {

        public boolean ignoreAnyInput = false;

        @Override
        public void handle(KeyEvent key) {

            if (ignoreAnyInput) {
                return;
            }

            if (key.getCode().getChar().equals("X")) {
                ignoreAnyInput = true;
                next(true);
            } else if (key.getCode().getChar().equals("C")) {
                ignoreAnyInput = true;
                next(false);
            } else if (key.getCode().getChar().equals("V")) {
                removeItemAddedManually();
            }
        }
    }
}
