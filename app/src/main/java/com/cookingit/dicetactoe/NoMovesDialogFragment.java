package com.cookingit.dicetactoe;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

public class NoMovesDialogFragment extends DialogFragment {

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
}
