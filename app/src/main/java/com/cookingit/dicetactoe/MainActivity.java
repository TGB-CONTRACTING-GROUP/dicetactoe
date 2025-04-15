package com.cookingit.dicetactoe;

//package com.cookingit.dicetactoe;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
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

import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private GameEngine gameEngine;
    private GridLayout gameBoard;
    private LinearLayout diceContainer;
    private TextView currentPlayerText, diceComboText, placementRuleText;
    private FirebaseManager firebaseManager;
    private boolean isOnlineMode = false;
    private boolean isMyTurn = false;
    private TextView playerXScoreText, playerOScoreText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        FirebaseAuth.getInstance().signInAnonymously();
        setContentView(R.layout.activity_main);

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
        showToast("Computer mode not fully implemented yet");
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

            // Set background with color and border using LayerDrawable
            boolean isKept = gameEngine.isDieKept(index);
            int backgroundColor = isKept ?
                    ContextCompat.getColor(this, android.R.color.holo_green_light) :
                    ContextCompat.getColor(this, android.R.color.transparent);
            Drawable borderDrawable = ContextCompat.getDrawable(this, R.drawable.cell_border);
            Drawable[] layers = new Drawable[] {
                    new ColorDrawable(backgroundColor), // Background color layer
                    borderDrawable // Border layer
            };
            LayerDrawable layerDrawable = new LayerDrawable(layers);
            dieView.setBackground(layerDrawable);

            // Make the die clickable only when appropriate
            if (canKeepDice) {
                dieView.setClickable(true);
                dieView.setOnClickListener(v -> {
                    // Toggle kept status
                    boolean newKeptStatus = !gameEngine.isDieKept(index);
                    gameEngine.setDieKeptStatus(index, newKeptStatus);
                    // Update background with new color and border
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
        // Update all game board cells
        for (int i = 0; i < 9; i++) {
            TextView cell = (TextView) gameBoard.getChildAt(i);
            int row = i / 3;
            int col = i % 3;
            cell.setText(gameEngine.getCellValue(row, col));
        }

        // Check if the game is over
        if (gameEngine.getGameState() == GameEngine.GameState.GAME_OVER) {
            String winner = gameEngine.getWinner();
            TextView diceInstruction = findViewById(R.id.dice_instruction);
            if (winner.equals("Draw")) {
                diceInstruction.setText("Game Over: It's a Draw!");
            } else {
                diceInstruction.setText(String.format("Game Over: Player %s Won!", winner));
            }
            // Disable the Roll Dice button
            Button rollBtn = findViewById(R.id.roll_btn);
            rollBtn.setEnabled(false);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
            // Update scores
            playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
            playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));
            return; // Skip updating turn-related UI elements
        }

        // Update current player and rolls left combined display
        String playerTurnText = String.format("Player %s’s turn – %d rolls left",
                gameEngine.getCurrentPlayer(), gameEngine.getRollsLeft());
        currentPlayerText.setText(playerTurnText);

        // Update dice instruction with a hint about tapping dice
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

        // Update roll button state
        Button rollBtn = findViewById(R.id.roll_btn);
        if (gameEngine.getRollsLeft() <= 0) {
            rollBtn.setEnabled(false);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
        } else {
            rollBtn.setEnabled(true);
            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_purple));
        }

        // Update skip button state
        Button skipBtn = findViewById(R.id.skip_btn);
        boolean enableSkip = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0;
        skipBtn.setEnabled(enableSkip);

        // Update dice combo and placement rule
        diceComboText.setText("Dice: " + (gameEngine.getCurrentCombination().isEmpty() ? "–" : gameEngine.getCurrentCombination()));
        placementRuleText.setText("Placement: " + (gameEngine.getPlacementRule() == null ? "–" : gameEngine.getPlacementRule()));

        // Update highlights
        updateValidCellsHighlight();

        playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
        playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));

        // Force UI refresh
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

        findViewById(R.id.toggle_hints).setOnClickListener(v -> {
            gameEngine.toggleHints();
        });
    }
}



//import android.os.Bundle;
//import android.view.Gravity;
//import android.view.View;
//import android.widget.CheckBox;
//import android.widget.GridLayout;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//import android.widget.Button;
//import android.util.Log;
//import android.graphics.drawable.Drawable;
//import android.graphics.drawable.LayerDrawable;
//import android.graphics.drawable.ColorDrawable;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.app.AlertDialog;
//import androidx.core.content.ContextCompat;
//
//import com.cookingit.dicetactoe.firebase.FirebaseManager;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.database.FirebaseDatabase;
//
//import android.widget.Toast;
//
//import java.util.List;
//
//public class MainActivity extends AppCompatActivity {
//
//    private GameEngine gameEngine;
//    private GridLayout gameBoard;
//    private LinearLayout diceContainer;
//    private TextView currentPlayerText, diceComboText, placementRuleText;
//    private FirebaseManager firebaseManager;
//    private boolean isOnlineMode = false;
//    private boolean isMyTurn = false;
//    private TextView playerXScoreText, playerOScoreText;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
//        FirebaseAuth.getInstance().signInAnonymously();
//        setContentView(R.layout.activity_main);
//
//        gameEngine = new GameEngine();
//        initializeUIComponents();
//        setupGameBoard();
//        setupButtonListeners();
//    }
//
//    private void initializeUIComponents() {
//        gameBoard = findViewById(R.id.game_board);
//        diceContainer = findViewById(R.id.dice_container);
//        currentPlayerText = findViewById(R.id.current_player);
//        diceComboText = findViewById(R.id.dice_combo);
//        placementRuleText = findViewById(R.id.placement_rule);
//        playerXScoreText = findViewById(R.id.player_x_score);
//        playerOScoreText = findViewById(R.id.player_o_score);
//    }
//
//    private void setupGameBoard() {
//        gameBoard.removeAllViews();
//        for(int i = 0; i < 9; i++) {
//            TextView cell = new TextView(this);
//            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
//            params.width = 240;
//            params.height = 240;
//            params.setMargins(2, 2, 2, 2);
//            cell.setLayoutParams(params);
//            cell.setBackgroundResource(R.drawable.board_cell_bg);
//            cell.setGravity(Gravity.CENTER);
//            cell.setTextSize(24);
//
//            final int row = i / 3;
//            final int col = i % 3;
//            cell.setOnClickListener(v -> handleCellClick(row, col));
//
//            gameBoard.addView(cell);
//        }
//    }
//
//    private void showModeSelection() {
//        new AlertDialog.Builder(this)
//                .setTitle("Select Game Mode")
//                .setItems(new String[]{"Local PvP", "Online PvP", "vs Computer"}, (dialog, which) -> {
//                    switch (which) {
//                        case 0: // Local PvP
//                            isOnlineMode = false;
//                            startNewGame();
//                            break;
//                        case 1: // Online PvP
//                            setupOnlineGame();
//                            break;
//                        case 2: // vs Computer
//                            setupComputerGame();
//                            break;
//                    }
//                }).show();
//    }
//
//    private void setupComputerGame() {
//        isOnlineMode = false;
//        gameEngine.newGame();
//        findViewById(R.id.skip_btn).setEnabled(false);
//        updateBoardState();
//        updateDiceDisplay();
//        showToast("Computer mode not fully implemented yet");
//    }
//
//    private void startNewGame() {
//        gameEngine.newGame();
//        updateBoardState();
//        updateDiceDisplay();
//        findViewById(R.id.skip_btn).setEnabled(false);
//    }
//
//    private void setupOnlineGame() {
//        isOnlineMode = true;
//        firebaseManager = new FirebaseManager(this, gameEngine);
//        firebaseManager.createOnlineGame();
//        updateBoardState();
//        updateDiceDisplay();
//        findViewById(R.id.skip_btn).setEnabled(false);
//        showToast("Creating online game...");
//    }
//
//    private void showToast(String message) {
//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
//    }
//
//    private void updateDiceDisplay() {
//        diceContainer.removeAllViews();
//        int[] diceValues = gameEngine.getDiceValues();
//
//        boolean canKeepDice = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0;
//        Log.d("DiceTacToe", "Can keep dice: " + canKeepDice + ", hasDiceRolled: " + gameEngine.hasDiceRolled() + ", rollsLeft: " + gameEngine.getRollsLeft());
//
//        for (int i = 0; i < diceValues.length; i++) {
//            final int index = i;
//            TextView dieView = new TextView(this);
//            dieView.setText(String.valueOf(diceValues[index]));
//            dieView.setTextSize(24);
//            dieView.setPadding(42, 24, 42, 24);
//            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.WRAP_CONTENT,
//                    LinearLayout.LayoutParams.WRAP_CONTENT
//            );
//            params.setMargins(4, 0, 4, 0);
//            dieView.setLayoutParams(params);
//
//            // Set background with color and border using LayerDrawable
//            boolean isKept = gameEngine.isDieKept(index);
//            int backgroundColor = isKept ?
//                    ContextCompat.getColor(this, android.R.color.holo_green_light) :
//                    ContextCompat.getColor(this, android.R.color.transparent);
//            Drawable borderDrawable = ContextCompat.getDrawable(this, R.drawable.cell_border);
//            Drawable[] layers = new Drawable[] {
//                    new ColorDrawable(backgroundColor), // Background color layer
//                    borderDrawable // Border layer
//            };
//            LayerDrawable layerDrawable = new LayerDrawable(layers);
//            dieView.setBackground(layerDrawable);
//
//            // Make the die clickable only when appropriate
//            if (canKeepDice) {
//                dieView.setClickable(true);
//                dieView.setOnClickListener(v -> {
//                    // Toggle kept status
//                    boolean newKeptStatus = !gameEngine.isDieKept(index);
//                    gameEngine.setDieKeptStatus(index, newKeptStatus);
//                    // Update background with new color and border
//                    int newBackgroundColor = newKeptStatus ?
//                            ContextCompat.getColor(this, android.R.color.holo_green_light) :
//                            ContextCompat.getColor(this, android.R.color.transparent);
//                    Drawable[] newLayers = new Drawable[] {
//                            new ColorDrawable(newBackgroundColor),
//                            ContextCompat.getDrawable(this, R.drawable.cell_border)
//                    };
//                    LayerDrawable newLayerDrawable = new LayerDrawable(newLayers);
//                    dieView.setBackground(newLayerDrawable);
//                    Log.d("DiceTacToe", "Die " + index + " clicked, new kept status: " + newKeptStatus);
//                });
//            } else {
//                dieView.setClickable(false);
//            }
//
//            diceContainer.addView(dieView);
//        }
//    }
//
//    public void updateBoardState() {
//        // Update all game board cells
//        for (int i = 0; i < 9; i++) {
//            TextView cell = (TextView) gameBoard.getChildAt(i);
//            int row = i / 3;
//            int col = i % 3;
//            cell.setText(gameEngine.getCellValue(row, col));
//        }
//
//        // Check if the game is over
//        if (gameEngine.getGameState() == GameEngine.GameState.GAME_OVER) {
//            String winner = gameEngine.getWinner();
//            TextView diceInstruction = findViewById(R.id.dice_instruction);
//            if (winner.equals("Draw")) {
//                diceInstruction.setText("Game Over: It's a Draw!");
//            } else {
//                diceInstruction.setText(String.format("Game Over: Player %s Won!", winner));
//            }
//            // Disable the Roll Dice button
//            Button rollBtn = findViewById(R.id.roll_btn);
//            rollBtn.setEnabled(false);
//            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
//            // Update scores
//            playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
//            playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));
//            return; // Skip updating turn-related UI elements
//        }
//
//        // Update current player and rolls left combined display
//        String playerTurnText = String.format("Player %s’s turn – %d rolls left",
//                gameEngine.getCurrentPlayer(), gameEngine.getRollsLeft());
//        currentPlayerText.setText(playerTurnText);
////        currentPlayerText.setTextColor(gameEngine.getCurrentPlayer().equals("X") ?
////                ContextCompat.getColor(this, android.R.color.holo_green_dark) :
////                ContextCompat.getColor(this, android.R.color.holo_blue_dark));
//
//        // Update dice instruction with a hint about tapping dice
//        TextView diceInstruction = findViewById(R.id.dice_instruction);
//        if (!gameEngine.hasDiceRolled()) {
//            diceInstruction.setText(String.format("Player %s, roll the dice to start your turn",
//                    gameEngine.getCurrentPlayer()));
//        } else if (gameEngine.getRollsLeft() == 0) {
//            diceInstruction.setText(String.format("Player %s, select a cell to place your mark",
//                    gameEngine.getCurrentPlayer()));
//        } else {
//            diceInstruction.setText(String.format("Player %s, tap dice to keep them, then click ‘Roll Dice’",
//                    gameEngine.getCurrentPlayer()));
//        }
//
//        // Update roll button state
//        Button rollBtn = findViewById(R.id.roll_btn);
//        if (gameEngine.getRollsLeft() <= 0) {
//            rollBtn.setEnabled(false);
//            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.controlBackground));
//        } else {
//            rollBtn.setEnabled(true);
//            rollBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_purple));
//        }
//
//        // Update skip button state
//        Button skipBtn = findViewById(R.id.skip_btn);
//        boolean enableSkip = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0;
//        skipBtn.setEnabled(enableSkip);
//
//        // Update dice combo and placement rule
//        diceComboText.setText("Dice: " + (gameEngine.getCurrentCombination().isEmpty() ? "–" : gameEngine.getCurrentCombination()));
//        placementRuleText.setText("Placement: " + (gameEngine.getPlacementRule() == null ? "–" : gameEngine.getPlacementRule()));
//
//        // Update highlights
//        updateValidCellsHighlight();
//
//        playerXScoreText.setText(String.valueOf(gameEngine.getPlayerXScore()));
//        playerOScoreText.setText(String.valueOf(gameEngine.getPlayerOScore()));
//
//        // Force UI refresh
//        currentPlayerText.invalidate();
//        currentPlayerText.requestLayout();
//    }
//
//    private void handleCellClick(int row, int col) {
//        if (isOnlineMode) {
//            if (isMyTurn && gameEngine.isValidMove(row, col)) {
//                firebaseManager.sendMove(row, col);
//                gameEngine.makeMove(row, col);
//                updateBoardState();
//            }
//        } else {
//            if (gameEngine.isValidMove(row, col)) {
//                gameEngine.makeMove(row, col);
//                updateBoardState();
//                runOnUiThread(() -> {
//                    String playerTurnText = String.format("Player %s’s turn – %d rolls left",
//                            gameEngine.getCurrentPlayer(), gameEngine.getRollsLeft());
//                    currentPlayerText.setText(playerTurnText);
//                    currentPlayerText.setTextColor(gameEngine.getCurrentPlayer().equals("X") ?
//                            ContextCompat.getColor(this, android.R.color.holo_green_dark) :
//                            ContextCompat.getColor(this, android.R.color.holo_blue_dark));
//                    currentPlayerText.invalidate();
//                    currentPlayerText.requestLayout();
//                });
//            }
//        }
//    }
//
//    private void showNoMovesDialog() {
//        NoMovesDialogFragment dialog = new NoMovesDialogFragment();
//        dialog.setGameEngine(gameEngine);
//        dialog.show(getSupportFragmentManager(), "NoMovesDialog");
//    }
//
//    private void updateValidCellsHighlight() {
//        List<int[]> validPositions = gameEngine.getValidPositions();
//        for(int i = 0; i < 9; i++) {
//            TextView cell = (TextView) gameBoard.getChildAt(i);
//            int row = i / 3;
//            int col = i % 3;
//            boolean isValid = false;
//
//            for(int[] pos : validPositions) {
//                if(pos[0] == row && pos[1] == col) {
//                    isValid = true;
//                    break;
//                }
//            }
//
//            if(isValid) {
//                cell.setBackgroundResource(R.drawable.valid_cell_bg);
//            } else {
//                cell.setBackgroundResource(R.drawable.board_cell_bg);
//            }
//        }
//    }
//
//    private void setupButtonListeners() {
//        findViewById(R.id.roll_btn).setOnClickListener(v -> {
//            if (gameEngine.rollDice()) {
//                showNoMovesDialog();
//            }
//            updateBoardState();
//            updateDiceDisplay();
//        });
//
//        findViewById(R.id.skip_btn).setOnClickListener(v -> {
//            if (gameEngine.skipRolls()) {
//                showNoMovesDialog();
//            }
//            updateBoardState();
//        });
//
//        findViewById(R.id.new_game).setOnClickListener(v -> {
//            gameEngine.newGame();
//            updateBoardState();
//            updateDiceDisplay();
//            findViewById(R.id.skip_btn).setEnabled(false);
//        });
//
//        findViewById(R.id.toggle_hints).setOnClickListener(v -> {
//            gameEngine.toggleHints();
//        });
//    }
//}