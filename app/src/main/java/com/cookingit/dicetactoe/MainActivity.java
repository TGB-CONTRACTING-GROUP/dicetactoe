package com.cookingit.dicetactoe;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cookingit.dicetactoe.firebase.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
//import android.support.v7.app.AlertDialog; // Add this line
import androidx.appcompat.app.AlertDialog; // Add this line
import android.widget.Toast;

import java.util.List;


public class MainActivity extends AppCompatActivity {

    private GameEngine gameEngine;
    private GridLayout gameBoard;
    private LinearLayout diceContainer;
    private TextView currentPlayerText, rollsLeftText, diceComboText, placementRuleText;
    private FirebaseManager firebaseManager;
    private boolean isOnlineMode = false;
    private boolean isMyTurn = false;

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
        rollsLeftText = findViewById(R.id.rolls_left);
        diceComboText = findViewById(R.id.dice_combo);
        placementRuleText = findViewById(R.id.placement_rule);
    }

    private void setupGameBoard() {
        gameBoard.removeAllViews();
        for(int i = 0; i < 9; i++) {
            TextView cell = new TextView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 180;
            params.height = 180;
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

    // mode selection dialog
    private void showModeSelection() {
        new AlertDialog.Builder(this)
                .setTitle("Select Game Mode")
                .setItems(new String[]{"Local PvP", "Online PvP", "vs Computer"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // Local PvP
                            isOnlineMode = false;
                            startNewGame();      // gameEngine.newGame();
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

        // Computer-specific initialization
        findViewById(R.id.skip_btn).setEnabled(false); // Computer doesn't need dice mechanics
        updateBoardState();
        updateDiceDisplay();

        // TODO: Add computer AI logic
        showToast("Computer mode not fully implemented yet");
    }

    private void startNewGame() {
        gameEngine.newGame();
        updateBoardState();
        updateDiceDisplay();
        findViewById(R.id.skip_btn).setEnabled(false);

        // Reset UI elements
        currentPlayerText.setText(getString(R.string.current_player_template, "X"));
        rollsLeftText.setText(getString(R.string.rolls_left_template, 3));
    }

    private void setupOnlineGame() {
        isOnlineMode = true;
        firebaseManager = new FirebaseManager(this, gameEngine);
        firebaseManager.createOnlineGame();

        // Online-specific UI setup
        updateBoardState();
        updateDiceDisplay();
        findViewById(R.id.skip_btn).setEnabled(false);
        showToast("Creating online game...");
    }

    // Add helper method for toasts
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateDiceDisplay() {
        diceContainer.removeAllViews();
        int[] diceValues = gameEngine.getDiceValues();

        for(int i = 0; i < 5; i++) {
            final int index = i; // Create final copy for lambda
            CheckBox dieCheckbox = new CheckBox(this);
            dieCheckbox.setText(String.valueOf(diceValues[index]));
            dieCheckbox.setChecked(gameEngine.isDieKept(index));
            dieCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                    gameEngine.setDieKeptStatus(index, isChecked)
            );

            diceContainer.addView(dieCheckbox);
        }
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
//        if(gameEngine.isValidMove(row, col)) {
//            gameEngine.makeMove(row, col);
//            updateBoardState();
//        }
    }

    public void updateBoardState() {
        // Update all game board cells
        for(int i = 0; i < 9; i++) {
            TextView cell = (TextView) gameBoard.getChildAt(i);
            int row = i / 3;
            int col = i % 3;
            cell.setText(gameEngine.getCellValue(row, col));
        }

        // current player display
        currentPlayerText.setText(
                getString(R.string.current_player_template, gameEngine.getCurrentPlayer())
        );

        // Update skip button state
        Button skipBtn = (Button) findViewById(R.id.skip_btn);
        boolean enableSkip = gameEngine.hasDiceRolled() && gameEngine.getRollsLeft() > 0;
        skipBtn.setEnabled(enableSkip);

        //Update rolls left display
        rollsLeftText.setText(
                getString(R.string.rolls_left_template, gameEngine.getRollsLeft())
        );
        //rollsLeftText.setText("Rolls Left: " + gameEngine.getRollsLeft());
        diceComboText.setText(gameEngine.getCurrentCombination());
        placementRuleText.setText(gameEngine.getPlacementRule());

        // Update highlights
        updateValidCellsHighlight();
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
            gameEngine.rollDice();
            updateBoardState();
            updateDiceDisplay();
        });

        findViewById(R.id.skip_btn).setOnClickListener(v -> {
            gameEngine.skipRolls();
            updateBoardState();
        });

        findViewById(R.id.new_game).setOnClickListener(v -> {
            gameEngine.newGame();
            updateBoardState();
            updateDiceDisplay();
            findViewById(R.id.skip_btn).setEnabled(false); // Disable on new game
        });

        findViewById(R.id.toggle_hints).setOnClickListener(v -> {
            gameEngine.toggleHints();
            findViewById(R.id.hints_panel).setVisibility(
                    gameEngine.hintsEnabled() ? View.VISIBLE : View.GONE
            );
        });
    }



    /*
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    */

    /*
    void updateDiceDisplay() {
        LinearLayout diceContainer = findViewById(R.id.dice_container);
        diceContainer.removeAllViews();
        GameEngine gameEngine = new GameEngine();

        for(int i=0; i<5; i++) {
            CheckBox die = new CheckBox(this);
            die.setText(String.valueOf(gameEngine.getDice()[i]));
            die.setChecked(gameEngine.isDieKept(i));
            die.setOnCheckedChangeListener((buttonView, isChecked) ->
                    gameEngine.setKeptDie(i, isChecked));
            diceContainer.addView(die);
        }
    }
    */

}