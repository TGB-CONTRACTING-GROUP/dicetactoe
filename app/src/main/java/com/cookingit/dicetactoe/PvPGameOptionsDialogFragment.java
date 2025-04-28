package com.cookingit.dicetactoe;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

public class PvPGameOptionsDialogFragment extends DialogFragment  {

    public interface PvPGameOptionsListener {
        void onLeaveGameSelected();
    }

    private PvPGameOptionsListener listener;

    public void setListener(PvPGameOptionsListener listener) {
        this.listener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("PvP Game Options")
                .setItems(new String[]{"Leave Game"}, (dialog, which) -> {
                    if (which == 0 && listener != null) {
                        confirmLeaveGame();
                    }
                })
                .setNegativeButton("Cancel", (dialog, id) -> {
                    // User cancelled the dialog
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                });

        return builder.create();
    }

    private void confirmLeaveGame() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Leave Game")
                .setMessage("Are you sure you want to leave this game? Your opponent will win.")
                .setPositiveButton("Leave", (dialog, which) -> {
                    if (listener != null) {
                        listener.onLeaveGameSelected();
                    }
                })
                .setNegativeButton("Stay", null)
                .show();
    }

}
