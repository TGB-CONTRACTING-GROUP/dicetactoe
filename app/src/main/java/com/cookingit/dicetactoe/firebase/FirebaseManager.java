package com.cookingit.dicetactoe.firebase;

//import com.google.firebase.database.*;
//import com.google

import com.cookingit.dicetactoe.GameEngine;
import com.cookingit.dicetactoe.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.*;


public class FirebaseManager {
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private GameEngine gameEngine;
    private MainActivity activity;
    private String gameId;
    private String playerId;

    public FirebaseManager(MainActivity activity, GameEngine gameEngine) {
        this.activity = activity;
        this.gameEngine = gameEngine;
        this.playerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        /*if (currentUser != null) {
            this.playerId = currentUser.getUid();
        } else {
            // Handle unauthenticated state
            activity.startLoginActivity(); // Example: Redirect to login
            throw new IllegalStateException("User not authenticated");
        }*/
    }

    public void createOnlineGame() {
        gameId = dbRef.child("games").push().getKey();
        dbRef.child("games").child(gameId).child("players").child(playerId).setValue(true);
        dbRef.child("games").child(gameId).child("status").setValue("waiting");
        setupGameListeners();
    }

    public void joinOnlineGame(String gameId) {
        this.gameId = gameId;
        dbRef.child("games").child(gameId).child("players").child(playerId).setValue(true);
        setupGameListeners();
    }

    private void setupGameListeners() {
        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                GameManager gameState = snapshot.getValue(GameManager.class);
                if (gameState != null) {
                    activity.runOnUiThread(() -> updateLocalGame(gameState));
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Handle error
            }
        });
    }

    private void updateLocalGame(GameManager remoteState) {
        // Update game engine state from Firebase
        gameEngine.syncWithRemote(remoteState);
        activity.updateBoardState();
    }

    public void sendMove(int row, int col) {
        if (gameId != null) {
            dbRef.child("games").child(gameId).child("board").child(row+"_"+col)
                    .setValue(gameEngine.getCurrentPlayer());
        }
    }

    public void updateDiceState(int[] dice, String currentCombo) {
        dbRef.child("games").child(gameId).child("dice").setValue(dice);
        dbRef.child("games").child(gameId).child("currentCombo").setValue(currentCombo);
    }
}
