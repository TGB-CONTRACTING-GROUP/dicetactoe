package com.cookingit.dicetactoe;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import androidx.gridlayout.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ColorDrawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.cookingit.dicetactoe.firebase.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.ViewGroup;

import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements PvPGameOptionsDialogFragment.PvPGameOptionsListener {

    private GameEngine gameEngine;
    private GridLayout gameBoard;
    private LinearLayout diceContainer;
    private TextView currentPlayerText, diceComboText, placementRuleText;
    private FirebaseManager firebaseManager;
    private boolean isOnlineMode = false;
    private boolean isMyTurn = false;
    private TextView playerXScoreText, playerOScoreText;
    private String trainingDifficulty = "easy";
    private String pvpDifficulty = "easy";
    private String aiDifficulty = "easy";
    private String gameHint = "";
    private boolean soundEnabled = true;
    private boolean musicEnabled = true;
    private String theme = "default";
    private String language = "English";
    private boolean isVsAI = false;
    private boolean isAITurnInProgress = false;
    private static final int MAX_AUTH_RETRIES = 3;
    private Toast currentToast;
    private boolean isAuthenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gameBoard = findViewById(R.id.game_board);

        FirebaseDatabase.getInstance().setLogLevel(com.google.firebase.database.Logger.Level.DEBUG);
        FirebaseDatabase.getInstance().setPersistenceEnabled(false);
        //FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        gameEngine = new GameEngine();
        initializeUIComponents();
        setupGameBoard();

        // Sign in anonymously with retry
        trySignInAnonymously(0);
    }

    private void trySignInAnonymously(int attempt) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d("DiceTacToe", "Anonymous sign-in successful");
                        isAuthenticated = true;
                        setupButtonListeners();
                        showTrainingDifficultySelection();
                    } else {
                        Log.e("DiceTacToe", "Anonymous sign-in failed", task.getException());
                        if (attempt < MAX_AUTH_RETRIES - 1) {
                            Log.d("DiceTacToe", "Retrying sign-in, attempt " + (attempt + 1));
                            trySignInAnonymously(attempt + 1);
                        } else {
                            showToast("Authentication failed after " + MAX_AUTH_RETRIES + " attempts. Please check your setup.");
                        }
                    }
                });
    }

//    private void trySignInAnonymously(int attempt) {
//        FirebaseAuth.getInstance().signInAnonymously()
//                .addOnCompleteListener(this, task -> {
//                    if (task.isSuccessful()) {
//                        Log.d("DiceTacToe", "Anonymous sign-in successful");
//                        setupButtonListeners();
//                        // Start in Training mode by default
//                        showTrainingDifficultySelection();
//                    } else {
//                        Log.e("DiceTacToe", "Anonymous sign-in failed", task.getException());
//                        if (attempt < MAX_AUTH_RETRIES - 1) {
//                            Log.d("DiceTacToe", "Retrying sign-in, attempt " + (attempt + 1));
//                            trySignInAnonymously(attempt + 1);
//                        } else {
//                            showToast("Authentication failed after " + MAX_AUTH_RETRIES + " attempts. Please check your setup.");
//                        }
//                    }
//                });
//    }

    private void initializeUIComponents() {
        gameBoard = findViewById(R.id.game_board);
        diceContainer = findViewById(R.id.dice_container);
        currentPlayerText = findViewById(R.id.current_player);
        diceComboText = findViewById(R.id.dice_combo);
        placementRuleText = findViewById(R.id.placement_rule);
        playerXScoreText = findViewById(R.id.player_x_score);
        playerOScoreText = findViewById(R.id.player_o_score);
    }

    private void setupGameBoard() {
        gameBoard.removeAllViews();
        for (int i = 0; i < 9; i++) {
            TextView cell = new TextView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 240;
            params.height = 240;
            params.setMargins(2, 2, 2, 2);
            cell.setLayoutParams(params);
            cell.setBackgroundResource(R.drawable.board_cell_bg);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(24);

            final int row = i / 3;
            final int col = i % 3;
            cell.setOnClickListener(v -> handleCellClick(row, col));

            gameBoard.addView(cell);
        }
    }

    public void setMyTurn(boolean myTurn) {
        this.isMyTurn = myTurn;
        updateBoardState();
    }

    private void showMoreOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("More Options");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        String[] gameModes = {"Training", "PvP", "Single Player vs. AI", "Game Hints"}; //, "Multiplayer", "Timed Mode", "Custom Rules"};
        builder.setItems(gameModes, (dialog, which) -> {
            switch (which) {
                case 0: // Training
                    showTrainingDifficultySelection();
                    break;
                case 1: // PvP
                    showPvPDifficultySelection();
                    break;
                case 2: // Single Player vs. AI
                    showAIDifficultySelection();
                    break;
                case 3: // Game Hints
                    gameHintsOptions();
                    break;
//                case 4: // Multiplayer
//                    showMultiplayerOptions();
//                    break;
//                case 5: // Timed Mode
//                    startTimedMode();
//                    break;
//                case 6: // Custom Rules
//                    showCustomRulesOptions();
//                    break;
            }
        });

//        TextView gameModesTitle = new TextView(this);
//        gameModesTitle.setText("Game Modes");
//        gameModesTitle.setTextSize(18);
//        gameModesTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
//        gameModesTitle.setPadding(0, 16, 0, 8);
//        layout.addView(gameModesTitle);

//        TextView settingsTitle = new TextView(this);
//        settingsTitle.setText("Settings");
//        settingsTitle.setTextSize(18);
//        settingsTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
//        settingsTitle.setPadding(0, 16, 0, 8);
//        layout.addView(settingsTitle);
//
//        Button settingsButton = new Button(this);
//        settingsButton.setText("Configure Settings");
//        settingsButton.setOnClickListener(v -> showSettingsDialog());
//        layout.addView(settingsButton);
//
//        TextView statsTitle = new TextView(this);
//        statsTitle.setText("Statistics and Leaderboards");
//        statsTitle.setTextSize(18);
//        statsTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
//        statsTitle.setPadding(0, 16, 0, 8);
//        layout.addView(statsTitle);
//
//        Button statsButton = new Button(this);
//        statsButton.setText("View Stats and Leaderboards");
//        statsButton.setOnClickListener(v -> showStatsAndLeaderboards());
//        layout.addView(statsButton);
//
//        TextView helpTitle = new TextView(this);
//        helpTitle.setText("Tutorials or Help");
//        helpTitle.setTextSize(18);
//        helpTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
//        helpTitle.setPadding(0, 16, 0, 8);
//        layout.addView(helpTitle);
//
//        Button helpButton = new Button(this);
//        helpButton.setText("View Tutorials and Tips");
//        helpButton.setOnClickListener(v -> showTutorialsAndHelp());
//        layout.addView(helpButton);
//
//        TextView socialTitle = new TextView(this);
//        socialTitle.setText("Social Features");
//        socialTitle.setTextSize(18);
//        socialTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
//        socialTitle.setPadding(0, 16, 0, 8);
//        layout.addView(socialTitle);
//
//        Button socialButton = new Button(this);
//        socialButton.setText("Social Features");
//        socialButton.setOnClickListener(v -> showSocialFeatures());
//        layout.addView(socialButton);
//
//        TextView additionalGamesTitle = new TextView(this);
//        additionalGamesTitle.setText("Additional Games or Modes");
//        additionalGamesTitle.setTextSize(18);
//        additionalGamesTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
//        additionalGamesTitle.setPadding(0, 16, 0, 8);
//        layout.addView(additionalGamesTitle);
//
//        Button additionalGamesButton = new Button(this);
//        additionalGamesButton.setText("Explore Additional Games");
//        additionalGamesButton.setOnClickListener(v -> showAdditionalGames());
//        layout.addView(additionalGamesButton);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showTrainingDifficultySelection() {
        String[] difficulties = {"Easy", "Medium", "Hard"};
        new AlertDialog.Builder(this)
                .setTitle("Select Training Difficulty")
                .setItems(difficulties, (dialog, which) -> {
                    trainingDifficulty = difficulties[which].toLowerCase();
                    setupTrainingGame();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // If the user cancels, still start in Training mode with default difficulty (easy)
                    trainingDifficulty = "easy";
                    setupTrainingGame();
                })
                .setCancelable(false) // Prevent dismissing without selecting a difficulty
                .show();
    }

    private void setupTrainingGame() {
        isOnlineMode = false;
        isVsAI = false;
        startNewGame();
        showToast("Starting Training mode on " + trainingDifficulty + " difficulty");
    }

    private void showPvPDifficultySelection() {
        if (!isAuthenticated) {
            showToast("Please wait, signing in...");
            return;
        }
        String[] difficulties = {"Easy", "Medium", "Hard"};
        new AlertDialog.Builder(this)
                .setTitle("Select PvP Difficulty")
                .setItems(difficulties, (dialog, which) -> {
                    pvpDifficulty = difficulties[which].toLowerCase();
                    setupOnlineGame();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Add this method to display a "Leave Game" option in the PvP mode
    private void showPvPGameOptions() {
        if (!isOnlineMode) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("PvP Game Options");

        String[] options = {"Leave Game"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) { // Leave Game
                confirmLeaveGame();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAIDifficultySelection() {
        String[] difficulties = {"Easy", "Medium", "Hard"};
        new AlertDialog.Builder(this)
                .setTitle("Select AI Difficulty")
                .setItems(difficulties, (dialog, which) -> {
                    aiDifficulty = difficulties[which].toLowerCase();
                    setupComputerGame();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void gameHintsOptions() {
        String[] gameHintsOptions = {
                "Four/Five of a Kind: Any square",
                "Full House: Any corner or center",
                "Straight: Middle row or column",
                "Three of a Kind: Any square except center",
                "Two Pair: Any corner square",
                "One Pair: Any edge square",
                "All Different: Only center square"
        };

        CharSequence[] styledHints = new CharSequence[gameHintsOptions.length];
        for (int i = 0; i < gameHintsOptions.length; i++) {
            SpannableString spannable = new SpannableString(gameHintsOptions[i]);
            int colonIndex = gameHintsOptions[i].indexOf(":");
            if (colonIndex > 0) {
                spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, colonIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            styledHints[i] = spannable;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView diceHeader = new TextView(this);
        diceHeader.setText("Dice Combinations");
        diceHeader.setTextSize(18);
        diceHeader.setTypeface(null, Typeface.BOLD);
        layout.addView(diceHeader);

        ListView listView = new ListView(this);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, styledHints
        );
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            gameHint = gameHintsOptions[position].toLowerCase();
        });
        layout.addView(listView);

        TextView placementHeader = new TextView(this);
        placementHeader.setText("\nPlacement Rules");
        placementHeader.setTextSize(18);
        placementHeader.setTypeface(null, Typeface.BOLD);
        layout.addView(placementHeader);

        GridLayout placementGrid = new GridLayout(this);
        placementGrid.setRowCount(3);
        placementGrid.setColumnCount(3);
        placementGrid.setPadding(8, 8, 8, 8);

        String[] placementLabels = {
                "C", "E", "C",
                "E", "Center", "E",
                "C", "E", "C"
        };

        for (int i = 0; i < 9; i++) {
            TextView cell = new TextView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 120;
            params.height = 120;
            params.setMargins(4, 4, 4, 4);
            cell.setLayoutParams(params);
            cell.setText(placementLabels[i]);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(16);
            cell.setBackgroundResource(android.R.drawable.btn_default);
            placementGrid.addView(cell);
        }

        layout.addView(placementGrid);

        new AlertDialog.Builder(this)
                .setTitle("Game Hints")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMultiplayerOptions() {
        String[] multiplayerOptions = {"Pass-and-Play", "Local Wi-Fi", "Online Matchmaking"};
        new AlertDialog.Builder(this)
                .setTitle("Multiplayer Options")
                .setItems(multiplayerOptions, (dialog, which) -> {
                    switch (which) {
                        case 0: // Pass-and-Play
                            startNewGame();
                            break;
                        case 1: // Local Wi-Fi
                            showToast("Local Wi-Fi mode not implemented yet");
                            break;
                        case 2: // Online Matchmaking
                            setupOnlineGame();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Add this method to confirm if the player wants to leave
    private void confirmLeaveGame() {
        new AlertDialog.Builder(this)
                .setTitle("Leave Game")
                .setMessage("Are you sure you want to leave this game? Your opponent will win.")
                .setPositiveButton("Leave", (dialog, which) -> {
                    if (firebaseManager != null) {
                        firebaseManager.leaveGame();
                        displayGameLeftDialog(false);
                    }
                })
                .setNegativeButton("Stay", null)
                .show();
    }

    public void displayGameLeftDialog(boolean youWon) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Game Ended");

        if (youWon) {
            builder.setMessage("Your opponent left the game. You win!");
        } else {
            builder.setMessage("You left the game.");
        }

        builder.setPositiveButton("New Game", (dialog, which) -> {
            if (isOnlineMode) {
                if (firebaseManager != null) {
                    firebaseManager.cleanup();
                }
                showPvPDifficultySelection();
            } else {
                startNewGame();
            }
        });

        builder.setNegativeButton("Main Menu", (dialog, which) -> {
            if (isOnlineMode && firebaseManager != null) {
                firebaseManager.cleanup();
            }
            isOnlineMode = false;
            showTrainingDifficultySelection();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Override onDestroy to clean up Firebase connections
    @Override
    protected void onDestroy() {
        if (isOnlineMode && firebaseManager != null) {
            firebaseManager.cleanup();
        }
        super.onDestroy();
    }

    // Override onStop to update player status when app is in background
    @Override
    protected void onStop() {
        super.onStop();
        // If we're in online mode, we should update our status to show we're not active
        if (isOnlineMode && firebaseManager != null && !isFinishing()) {
            // No need to fully leave the game, the heartbeat mechanism will handle temporary absence
        }
    }

    // Override onResume to update player status when app comes to foreground
    @Override
    protected void onResume() {
        super.onResume();
        // If we're in online mode, update our status to show we're active again
        if (isOnlineMode && firebaseManager != null) {
            // The next heartbeat will update our timestamp
        }
    }

    private void startTimedMode() {
        showToast("Timed Mode not fully implemented yet");
        startNewGame();
    }

    private void showCustomRulesOptions() {
        String[] customRules = {"Change Number of Dice", "Modify Grid Size"};
        new AlertDialog.Builder(this)
                .setTitle("Custom Rules")
                .setItems(customRules, (dialog, which) -> {
                    switch (which) {
                        case 0: // Change Number of Dice
                            showToast("Changing number of dice not implemented yet");
                            break;
                        case 1: // Modify Grid Size
                            showToast("Modifying grid size not implemented yet");
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        CheckBox soundCheckBox = new CheckBox(this);
        soundCheckBox.setText("Sound Effects");
        soundCheckBox.setChecked(soundEnabled);
        soundCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> soundEnabled = isChecked);
        layout.addView(soundCheckBox);

        CheckBox musicCheckBox = new CheckBox(this);
        musicCheckBox.setText("Background Music");
        musicCheckBox.setChecked(musicEnabled);
        musicCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> musicEnabled = isChecked);
        layout.addView(musicCheckBox);

        Button themeButton = new Button(this);
        themeButton.setText("Select Theme: " + theme);
        themeButton.setOnClickListener(v -> {
            String[] themes = {"Default", "Dark", "Colorful"};
            new AlertDialog.Builder(this)
                    .setTitle("Select Theme")
                    .setItems(themes, (dialog, which) -> theme = themes[which].toLowerCase())
                    .show();
        });
        layout.addView(themeButton);

        Button languageButton = new Button(this);
        languageButton.setText("Select Language: " + language);
        languageButton.setOnClickListener(v -> {
            String[] languages = {"English", "Spanish", "French"};
            new AlertDialog.Builder(this)
                    .setTitle("Select Language")
                    .setItems(languages, (dialog, which) -> language = languages[which])
                    .show();
        });
        layout.addView(languageButton);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> showToast("Settings saved"));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showStatsAndLeaderboards() {
        showToast("Statistics and Leaderboards not implemented yet");
    }

    private void showTutorialsAndHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Tutorials and Help")
                .setMessage("DiceTacToe combines Tic-Tac-Toe with dice rolling. Roll the dice to determine where you can place your mark. Win by getting three in a row!")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showSocialFeatures() {
        String[] socialOptions = {"Invite Friends", "Share Progress", "View Achievements"};
        new AlertDialog.Builder(this)
                .setTitle("Social Features")
                .setItems(socialOptions, (dialog, which) -> {
                    showToast(socialOptions[which] + " not implemented yet");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAdditionalGames() {
        String[] additionalGames = {"Connect Four", "Checkers", "Yahtzee"};
        new AlertDialog.Builder(this)
                .setTitle("Additional Games")
                .setItems(additionalGames, (dialog, which) -> {
                    showToast(additionalGames[which] + " not implemented yet");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showModeSelection() {
        new AlertDialog.Builder(this)
                .setTitle("Select Game Mode")
                .setItems(new String[]{"Local PvP", "Online PvP", "vs Computer"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // Local PvP
                            isOnlineMode = false;
                            startNewGame();
                            break;
                        case 1: // Online PvP
                            setupOnlineGame();
                            break;
                        case 2: // vs Computer
                            setupComputerGame();
                            break;
                    }
                }).show();
    }

    private void setupOnlineGame() {
        if (!isAuthenticated) {
            showToast("Please wait, signing in...");
            trySignInAnonymouslyForOnlineGame(0);
        } else {
            initializeOnlineGame();
        }
    }

    private void trySignInAnonymouslyForOnlineGame(int attempt) {
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d("DiceTacToe", "Anonymous sign-in successful for online game");
                        initializeOnlineGame();
                    } else {
                        Log.e("DiceTacToe", "Anonymous sign-in failed for online game", task.getException());
                        if (attempt < MAX_AUTH_RETRIES - 1) {
                            Log.d("DiceTacToe", "Retrying sign-in for online game, attempt " + (attempt + 1));
                            trySignInAnonymouslyForOnlineGame(attempt + 1);
                        } else {
                            showToast("Failed to sign in after " + MAX_AUTH_RETRIES + " attempts. Please check your setup.");
                        }
                    }
                });
    }

    private void initializeOnlineGame() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showToast("Authentication required. Please try again.");
            return;
        }
        isOnlineMode = true;
        firebaseManager = new FirebaseManager(this, gameEngine);
        firebaseManager.findOrCreateGame();
        findViewById(R.id.skip_btn).setEnabled(false);
        updateBoardState();
        updateDiceDisplay();
        showToast("Finding or creating online game...");
    }

    private void setupComputerGame() {
        isOnlineMode = false;
        isVsAI = true;
        gameEngine.newGame();
        findViewById(R.id.skip_btn).setEnabled(false);
        updateBoardState();
        updateDiceDisplay();
        showToast("Playing against AI on " + aiDifficulty + " mode");
    }

    private void startNewGame() {
        gameEngine.newGame();
        if (!isOnlineMode && !isVsAI) {
            isMyTurn = true; // In Training mode, the human player controls both X and O
        }
        updateBoardState();
        updateDiceDisplay();
        findViewById(R.id.skip_btn).setEnabled(false);
    }

    private long lastToastTime = 0;
    private static final long TOAST_COOLDOWN_MS = 2000; // 2 seconds

    public void showToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
    }

    private void updateDiceDisplay() {
        diceContainer.removeAllViews();
        List<Integer> diceValues = gameEngine.getDiceValues(); // Changed from int[]
        //int[] diceValues = gameEngine.getDiceValues();

        boolean canKeepDice = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0 && isMyTurn;
        Log.d("DiceTacToe", "Can keep dice: " + canKeepDice + ", hasDiceRolled: " + gameEngine.hasDiceRolled() + ", rollsLeft: " + gameEngine.getRollsLeft());

        for (int i = 0; i < diceValues.size(); i++) {   // for (int i = 0; i < diceValues.length; i++)
            final int index = i;
            TextView dieView = new TextView(this);
            dieView.setText(String.valueOf(diceValues.get(index)));
            //dieView.setText(String.valueOf(diceValues[index]));
            dieView.setTextSize(24);
            dieView.setPadding(42, 24, 42, 24);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(4, 0, 4, 0);
            dieView.setLayoutParams(params);

            boolean isKept = gameEngine.isDieKept(index);
            int backgroundColor = isKept ?
                    ContextCompat.getColor(this, android.R.color.holo_green_light) :
                    ContextCompat.getColor(this, android.R.color.transparent);
            Drawable borderDrawable = ContextCompat.getDrawable(this, R.drawable.cell_border);
            Drawable[] layers = new Drawable[] {
                    new ColorDrawable(backgroundColor),
                    borderDrawable
            };
            LayerDrawable layerDrawable = new LayerDrawable(layers);
            dieView.setBackground(layerDrawable);

            if (canKeepDice) {
                dieView.setClickable(true);
                dieView.setOnClickListener(v -> {
                    boolean newKeptStatus = !gameEngine.isDieKept(index);
                    gameEngine.setDieKeptStatus(index, newKeptStatus);
                    int newBackgroundColor = newKeptStatus ?
                            ContextCompat.getColor(this, android.R.color.holo_green_light) :
                            ContextCompat.getColor(this, android.R.color.transparent);
                    Drawable[] newLayers = new Drawable[] {
                            new ColorDrawable(newBackgroundColor),
                            ContextCompat.getDrawable(this, R.drawable.cell_border)
                    };
                    LayerDrawable newLayerDrawable = new LayerDrawable(newLayers);
                    dieView.setBackground(newLayerDrawable);
                    Log.d("DiceTacToe", "Die " + index + " clicked, new kept status: " + newKeptStatus);
                    if (isOnlineMode) {
                        firebaseManager.updateDiceState(gameEngine.getDiceValues(), gameEngine.getCurrentCombination());
                    }
                });
            } else {
                dieView.setClickable(false);
            }

            diceContainer.addView(dieView);
        }
    }

    public void updateBoardState() {
        for (int i = 0; i < 9; i++) {
            TextView cell = (TextView) gameBoard.getChildAt(i);
            int row = i / 3;
            int col = i % 3;
            cell.setText(gameEngine.getCellValue(row, col));
        }

        if (gameEngine.getGameState() == GameEngine.GameState.GAME_OVER) {
            String winner = gameEngine.getWinner();
            TextView diceInstruction = findViewById(R.id.dice_instruction);
            if ("Draw".equals(winner)) { // Safe comparison: avoids NullPointerException
                diceInstruction.setText("Game Over: It's a Draw!");
            } else if (winner != null) {
                diceInstruction.setText(String.format("Game Over: Player %s Won!", winner));
            } else {
                diceInstruction.setText("Game Over: Ended");
            }
            Button rollBtn = findViewById(R.id.roll_btn);
            rollBtn.setEnabled(false);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
            playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
            playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));
            if (isOnlineMode) {
                firebaseManager.endGame();
            }
            return;
        }

        // Set isMyTurn for Player vs AI mode
        if (isVsAI && !isOnlineMode) {
            isMyTurn = gameEngine.getCurrentPlayer().equals("X"); // Player X is the human player
        }
        // Set isMyTurn for Training mode (Local PvP)
        else if (!isOnlineMode && !isVsAI) {
            isMyTurn = true; // In Training mode, the human player controls both X and O
        }

        // Trigger AI turn if applicable
        if (isVsAI && !isOnlineMode
                && gameEngine.getCurrentPlayer().equals("O")
                && gameEngine.getGameState() != GameEngine.GameState.GAME_OVER
                && !isAITurnInProgress) {
            new android.os.Handler().postDelayed(() -> handleAITurn(), 1000);
        }

        String playerTurnText = String.format("Player %s’s turn – %d rolls left%s",
                gameEngine.getCurrentPlayer(), gameEngine.getRollsLeft(), isOnlineMode ? (isMyTurn ? " (Your turn)" : " (Opponent's turn)") : "");
        currentPlayerText.setText(playerTurnText);

        TextView diceInstruction = findViewById(R.id.dice_instruction);
        if (!gameEngine.hasDiceRolled()) {
            diceInstruction.setText(String.format("Player %s, roll the dice to start your turn", gameEngine.getCurrentPlayer()));
        } else if (gameEngine.getRollsLeft() == 0) {
            diceInstruction.setText(String.format("Player %s, select a cell to place your mark", gameEngine.getCurrentPlayer()));
        } else {
            diceInstruction.setText(String.format("Player %s, tap dice to keep them, then click ‘Roll Dice’", gameEngine.getCurrentPlayer()));
        }

        Button rollBtn = findViewById(R.id.roll_btn);
        if (gameEngine.getRollsLeft() <= 0 || (isOnlineMode && !isMyTurn)) {
            rollBtn.setEnabled(false);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
        } else {
            rollBtn.setEnabled(true);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_purple));
        }

        Button skipBtn = findViewById(R.id.skip_btn);
        boolean enableSkip = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0 && (!isOnlineMode || isMyTurn);
        skipBtn.setEnabled(enableSkip);

        diceComboText.setText((gameEngine.getCurrentCombination().isEmpty() ? "–" : gameEngine.getCurrentCombination()));
        placementRuleText.setText((gameEngine.getPlacementRule() == null ? "–" : gameEngine.getPlacementRule()));

        updateValidCellsHighlight();

        playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
        playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));

        currentPlayerText.invalidate();
        currentPlayerText.requestLayout();
    }

    private void handleAITurn() {
        isAITurnInProgress = true;
        if (gameEngine.getGameState() == GameEngine.GameState.ROLLING) {
            rollAIDice();
        } else if (gameEngine.getGameState() == GameEngine.GameState.PLACING) {
            placeAIMark();
        }
    }

    private void rollAIDice() {
        if (gameEngine.getRollsLeft() <= 0 || gameEngine.getGameState() == GameEngine.GameState.GAME_OVER) {
            placeAIMark();
            return;
        }

        new android.os.Handler().postDelayed(() -> {
            boolean noMoves = gameEngine.rollDice();
            updateDiceDisplay();
            updateBoardState();

            if (noMoves) {
                gameEngine.makeMove(-1, -1);
                isAITurnInProgress = false;
                updateBoardState();
            } else {
                rollAIDice(); // Continue rolling
            }
        }, 1000);
    }

    private void placeAIMark() {
        List<int[]> validPositions = gameEngine.getValidPositions();
        if (validPositions.isEmpty()) {
            gameEngine.makeMove(-1, -1);
        } else {
            // Simple AI: Random selection (easy mode)
            int[] pos = validPositions.get((int) (Math.random() * validPositions.size()));
            gameEngine.makeMove(pos[0], pos[1]);
        }
        isAITurnInProgress = false;
        updateBoardState();
    }

    private void handleCellClick(int row, int col) {
        if (isVsAI && gameEngine.getCurrentPlayer().equals("O")) {
            showToast("AI's turn. Please wait.");
            return;
        }

        if (isOnlineMode) {
            if (isMyTurn && gameEngine.isValidMove(row, col) && gameEngine.getGameState() == GameEngine.GameState.PLACING) {
                firebaseManager.sendMove(row, col);
                gameEngine.makeMove(row, col);
                updateBoardState();
            } else {
                showToast("Not your turn, invalid move, or not in placing state");
            }
        } else {
            if (gameEngine.isValidMove(row, col)) {
                gameEngine.makeMove(row, col);
                updateBoardState();
            }
        }
    }

    private void showNoMovesDialog() {
        NoMovesDialogFragment dialog = new NoMovesDialogFragment();
        dialog.setGameEngine(gameEngine);
        dialog.show(getSupportFragmentManager(), "NoMovesDialog");
    }

    private void updateValidCellsHighlight() {
        List<int[]> validPositions = gameEngine.getValidPositions();
        for (int i = 0; i < 9; i++) {
            TextView cell = (TextView) gameBoard.getChildAt(i);
            int row = i / 3;
            int col = i % 3;
            boolean isValid = false;

            for (int[] pos : validPositions) {
                if (pos[0] == row && pos[1] == col) {
                    isValid = true;
                    break;
                }
            }

            if (isValid) {
                cell.setBackgroundResource(R.drawable.valid_cell_bg);
            } else {
                cell.setBackgroundResource(R.drawable.board_cell_bg);
            }
        }
    }

    private void setupButtonListeners() {

        findViewById(R.id.roll_btn).setOnClickListener(v -> {
            if (isOnlineMode && !isMyTurn) {
                showToast("Not your turn!");
                return;
            }
            if (gameEngine.rollDice()) {
                showNoMovesDialog();
                if (isOnlineMode) {
                    firebaseManager.sendMove(-1, -1); // End turn
                }
            }
            updateBoardState();
            updateDiceDisplay();
            if (isOnlineMode && gameEngine.getRollsLeft() > 0) {
                firebaseManager.updateDiceState(gameEngine.getDiceValues(), gameEngine.getCurrentCombination());
            }
        });

        findViewById(R.id.skip_btn).setOnClickListener(v -> {
            if (isOnlineMode && !isMyTurn) {
                showToast("Not your turn!");
                return;
            }
            if (gameEngine.skipRolls()) {
                showNoMovesDialog();
                if (isOnlineMode) {
                    firebaseManager.sendMove(-1, -1); // End turn
                }
            }
            updateBoardState();
            if (isOnlineMode && gameEngine.getRollsLeft() > 0) {
                firebaseManager.updateDiceState(gameEngine.getDiceValues(), gameEngine.getCurrentCombination());
            }
        });

        findViewById(R.id.new_game).setOnClickListener(v -> {
            if (isOnlineMode) {
                firebaseManager.endGame();
                setupOnlineGame();
            } else {
                gameEngine.newGame();
                updateBoardState();
                updateDiceDisplay();
                findViewById(R.id.skip_btn).setEnabled(false);
            }
        });

        findViewById(R.id.next_btn).setOnClickListener(v -> {
            if (isOnlineMode) {
                showPvPGameOptions();
            } else {
                // Handle other modes if needed
            }
        });

        findViewById(R.id.toggle_hints).setOnClickListener(v -> showMoreOptions());
    }

    @Override
    public void onLeaveGameSelected() {
        if (firebaseManager != null) {
            firebaseManager.leaveGame();
            displayGameLeftDialog(false);
        }
    }

    // Show options dialog when "Next" button is clicked in online mode
    private void showPvPOptions() {
        PvPGameOptionsDialogFragment dialog = new PvPGameOptionsDialogFragment();
        dialog.setListener(this);
        dialog.show(getSupportFragmentManager(), "PvPOptions");
    }


}