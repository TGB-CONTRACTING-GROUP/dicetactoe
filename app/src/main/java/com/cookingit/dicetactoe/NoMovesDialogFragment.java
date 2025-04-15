package com.cookingit.dicetactoe;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

public class NoMovesDialogFragment extends DialogFragment {

    private GameEngine gameEngine;

    // Method to set GameEngine instance
    public void setGameEngine(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("No Valid Moves")
                .setMessage("No valid moves available. Your turn will be skipped.")
                .setPositiveButton("OK", (dialog, id) -> {
                    if (gameEngine != null) {
                        // Simulate a move by switching player
                        gameEngine.makeMove(-1, -1); // Invalid move to trigger switchPlayer
                        ((MainActivity) getActivity()).updateBoardState();
                    }
                });
        return builder.create();
    }
}




/*public class NoMovesDialogFragment extends DialogFragment {

    private GameEngine gameEngine;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("No Valid Moves")
                .setMessage("No valid moves available. Your turn will be skipped.")
                .setPositiveButton("OK", (dialog, id) -> {
                    // Handle confirmation
                });
        return builder.create();
    }
}*/
