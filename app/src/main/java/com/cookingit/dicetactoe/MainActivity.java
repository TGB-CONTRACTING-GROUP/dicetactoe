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

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private GameEngine gameEngine;
    androidx.gridlayout.widget.GridLayout gameBoard;
    private LinearLayout diceContainer;
    private TextView currentPlayerText, diceComboText, placementRuleText;
    private FirebaseManager firebaseManager;
    private boolean isOnlineMode = false;
    private boolean isMyTurn = false;
    private TextView playerXScoreText, playerOScoreText;
    private String aiDifficulty = "easy"; // Default AI difficulty
    private String gameHint = "";   // Default game hint
    private boolean soundEnabled = true; // Default sound setting
    private boolean musicEnabled = true; // Default music setting
    private String theme = "default"; // Default theme
    private String language = "English"; // Default language

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gameBoard = findViewById(R.id.game_board);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        FirebaseAuth.getInstance().signInAnonymously();

        gameEngine = new GameEngine();
        initializeUIComponents();
        setupGameBoard();
        setupButtonListeners();
    }

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
        for(int i = 0; i < 9; i++) {
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

    private void showMoreOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("More Options");

        // Create a LinearLayout to hold all sections
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        // Game Modes Section
        TextView gameModesTitle = new TextView(this);
        gameModesTitle.setText("Game Modes");
        gameModesTitle.setTextSize(18);
        gameModesTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        gameModesTitle.setPadding(0, 16, 0, 8);
        layout.addView(gameModesTitle);

        String[] gameModes = {"Single Player vs. AI", "Multiplayer", "Timed Mode", "Game Hints", "Custom Rules"};
        builder.setItems(gameModes, (dialog, which) -> {
            switch (which) {
                case 0: // Single Player vs. AI
                    showAIDifficultySelection();
                    break;
                case 1: // Multiplayer
                    showMultiplayerOptions();
                    break;
                case 2: // Timed Mode
                    startTimedMode();
                    break;
                case 3: // Game Hints
                    gameHintsOptions();
                    break;
                case 4: // Custom Rules
                    showCustomRulesOptions();
                    break;
            }
        });

        // Settings Section
        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("Settings");
        settingsTitle.setTextSize(18);
        settingsTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        settingsTitle.setPadding(0, 16, 0, 8);
        layout.addView(settingsTitle);

        Button settingsButton = new Button(this);
        settingsButton.setText("Configure Settings");
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        layout.addView(settingsButton);

        // Statistics and Leaderboards Section
        TextView statsTitle = new TextView(this);
        statsTitle.setText("Statistics and Leaderboards");
        statsTitle.setTextSize(18);
        statsTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        statsTitle.setPadding(0, 16, 0, 8);
        layout.addView(statsTitle);

        Button statsButton = new Button(this);
        statsButton.setText("View Stats and Leaderboards");
        statsButton.setOnClickListener(v -> showStatsAndLeaderboards());
        layout.addView(statsButton);

        // Tutorials or Help Section
        TextView helpTitle = new TextView(this);
        helpTitle.setText("Tutorials or Help");
        helpTitle.setTextSize(18);
        helpTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        helpTitle.setPadding(0, 16, 0, 8);
        layout.addView(helpTitle);

        Button helpButton = new Button(this);
        helpButton.setText("View Tutorials and Tips");
        helpButton.setOnClickListener(v -> showTutorialsAndHelp());
        layout.addView(helpButton);

        // Social Features Section
        TextView socialTitle = new TextView(this);
        socialTitle.setText("Social Features");
        socialTitle.setTextSize(18);
        socialTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        socialTitle.setPadding(0, 16, 0, 8);
        layout.addView(socialTitle);

        Button socialButton = new Button(this);
        socialButton.setText("Social Features");
        socialButton.setOnClickListener(v -> showSocialFeatures());
        layout.addView(socialButton);

        // Additional Games or Modes Section
        TextView additionalGamesTitle = new TextView(this);
        additionalGamesTitle.setText("Additional Games or Modes");
        additionalGamesTitle.setTextSize(18);
        additionalGamesTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        additionalGamesTitle.setPadding(0, 16, 0, 8);
        layout.addView(additionalGamesTitle);

        Button additionalGamesButton = new Button(this);
        additionalGamesButton.setText("Explore Additional Games");
        additionalGamesButton.setOnClickListener(v -> showAdditionalGames());
        layout.addView(additionalGamesButton);

        // Add the layout to a scrollable view
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
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
        String[] gameHintsOptions = {"Four/Five of a Kind: Any square","Full House: Any corner or center",
                "Straight: Middle row or column", "Three of a Kind: Any square except center",
                "Two Pair: Any corner square", "One Pair: Any edge square", "All Different: Only center square "};

        // Create styled hints for dice combinations
        CharSequence[] styledHints = new CharSequence[gameHintsOptions.length];
        for (int i = 0; i < gameHintsOptions.length; i++) {
            SpannableString spannable = new SpannableString(gameHintsOptions[i]);
            int colonIndex = gameHintsOptions[i].indexOf(":");
            if (colonIndex > 0) {
                spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, colonIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            styledHints[i] = spannable;
        }

        // Build custom dialog layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        // Dice Combinations Section
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

        // Placement Rules Section
        TextView placementHeader = new TextView(this);
        placementHeader.setText("\nPlacement Rules");
        placementHeader.setTextSize(18);
        placementHeader.setTypeface(null, Typeface.BOLD);
        layout.addView(placementHeader);

        // Create a 3x3 GridLayout for Placement Rules
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
            params.width = 120;  // Adjust size as needed
            params.height = 120;
            params.setMargins(4, 4, 4, 4);
            cell.setLayoutParams(params);
            cell.setText(placementLabels[i]);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(16);
            cell.setBackgroundResource(android.R.drawable.btn_default); // Use a default button background for styling
            placementGrid.addView(cell);
        }

        layout.addView(placementGrid);

        // Display dialog
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

    private void startTimedMode() {
        showToast("Timed Mode not fully implemented yet");
        startNewGame();
        // Placeholder for timed mode logic
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

        // Sound Toggle
        CheckBox soundCheckBox = new CheckBox(this);
        soundCheckBox.setText("Sound Effects");
        soundCheckBox.setChecked(soundEnabled);
        soundCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> soundEnabled = isChecked);
        layout.addView(soundCheckBox);

        // Music Toggle
        CheckBox musicCheckBox = new CheckBox(this);
        musicCheckBox.setText("Background Music");
        musicCheckBox.setChecked(musicEnabled);
        musicCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> musicEnabled = isChecked);
        layout.addView(musicCheckBox);

        // Theme Selection
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

        // Language Selection
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
        // Placeholder for stats and leaderboards
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

    private void setupComputerGame() {
        isOnlineMode = false;
        gameEngine.newGame();
        findViewById(R.id.skip_btn).setEnabled(false);
        updateBoardState();
        updateDiceDisplay();
        showToast("Playing against AI on " + aiDifficulty + " mode");
    }

    private void startNewGame() {
        gameEngine.newGame();
        updateBoardState();
        updateDiceDisplay();
        findViewById(R.id.skip_btn).setEnabled(false);
    }

    private void setupOnlineGame() {
        isOnlineMode = true;
        firebaseManager = new FirebaseManager(this, gameEngine);
        firebaseManager.createOnlineGame();
        updateBoardState();
        updateDiceDisplay();
        findViewById(R.id.skip_btn).setEnabled(false);
        showToast("Creating online game...");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateDiceDisplay() {
        diceContainer.removeAllViews();
        int[] diceValues = gameEngine.getDiceValues();

        boolean canKeepDice = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0;
        Log.d("DiceTacToe", "Can keep dice: " + canKeepDice + ", hasDiceRolled: " + gameEngine.hasDiceRolled() + ", rollsLeft: " + gameEngine.getRollsLeft());

        for (int i = 0; i < diceValues.length; i++) {
            final int index = i;
            TextView dieView = new TextView(this);
            dieView.setText(String.valueOf(diceValues[index]));
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
            if (winner.equals("Draw")) {
                diceInstruction.setText("Game Over: It's a Draw!");
            } else {
                diceInstruction.setText(String.format("Game Over: Player %s Won!", winner));
            }
            Button rollBtn = findViewById(R.id.roll_btn);
            rollBtn.setEnabled(false);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
            playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
            playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));
            return;
        }

        String playerTurnText = String.format("Player %s’s turn – %d rolls left",
                gameEngine.getCurrentPlayer(), gameEngine.getRollsLeft());
        currentPlayerText.setText(playerTurnText);

        TextView diceInstruction = findViewById(R.id.dice_instruction);
        if (!gameEngine.hasDiceRolled()) {
            diceInstruction.setText(String.format("Player %s, roll the dice to start your turn",
                    gameEngine.getCurrentPlayer()));
        } else if (gameEngine.getRollsLeft() == 0) {
            diceInstruction.setText(String.format("Player %s, select a cell to place your mark",
                    gameEngine.getCurrentPlayer()));
        } else {
            diceInstruction.setText(String.format("Player %s, tap dice to keep them, then click ‘Roll Dice’",
                    gameEngine.getCurrentPlayer()));
        }

        Button rollBtn = findViewById(R.id.roll_btn);
        if (gameEngine.getRollsLeft() <= 0) {
            rollBtn.setEnabled(false);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
        } else {
            rollBtn.setEnabled(true);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_purple));
        }

        Button skipBtn = findViewById(R.id.skip_btn);
        boolean enableSkip = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0;
        skipBtn.setEnabled(enableSkip);

        diceComboText.setText((gameEngine.getCurrentCombination().isEmpty() ? "–" : gameEngine.getCurrentCombination()));
        placementRuleText.setText((gameEngine.getPlacementRule() == null ? "–" : gameEngine.getPlacementRule()));

        updateValidCellsHighlight();

        playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
        playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));

        currentPlayerText.invalidate();
        currentPlayerText.requestLayout();
    }

    private void handleCellClick(int row, int col) {
        if (isOnlineMode) {
            if (isMyTurn && gameEngine.isValidMove(row, col)) {
                firebaseManager.sendMove(row, col);
                gameEngine.makeMove(row, col);
                updateBoardState();
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
        for(int i = 0; i < 9; i++) {
            TextView cell = (TextView) gameBoard.getChildAt(i);
            int row = i / 3;
            int col = i % 3;
            boolean isValid = false;

            for(int[] pos : validPositions) {
                if(pos[0] == row && pos[1] == col) {
                    isValid = true;
                    break;
                }
            }

            if(isValid) {
                cell.setBackgroundResource(R.drawable.valid_cell_bg);
            } else {
                cell.setBackgroundResource(R.drawable.board_cell_bg);
            }
        }
    }

    private void setupButtonListeners() {
        findViewById(R.id.roll_btn).setOnClickListener(v -> {
            if (gameEngine.rollDice()) {
                showNoMovesDialog();
            }
            updateBoardState();
            updateDiceDisplay();
        });

        findViewById(R.id.skip_btn).setOnClickListener(v -> {
            if (gameEngine.skipRolls()) {
                showNoMovesDialog();
            }
            updateBoardState();
        });

        findViewById(R.id.new_game).setOnClickListener(v -> {
            gameEngine.newGame();
            updateBoardState();
            updateDiceDisplay();
            findViewById(R.id.skip_btn).setEnabled(false);
        });

        findViewById(R.id.toggle_hints).setOnClickListener(v -> showMoreOptions());
    }
}