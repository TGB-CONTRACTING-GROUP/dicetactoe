package com.cookingit.dicetactoe.firebase;

import android.util.Log;
import com.cookingit.dicetactoe.GameEngine;
import com.cookingit.dicetactoe.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class FirebaseManager {
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private final GameEngine gameEngine;
    private final MainActivity activity;
    private String gameId;
    private final String playerId;
    private boolean isPlayerX;

    public FirebaseManager(MainActivity activity, GameEngine gameEngine) {
        this.activity = activity;
        this.gameEngine = gameEngine;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No authenticated user found. Please sign in first.");
        }
        this.playerId = user.getUid();
    }

    public void createOnlineGame() {
        gameId = dbRef.child("games").push().getKey();
        isPlayerX = true;

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("status", "waiting");
        gameData.put("currentPlayer", "X");
        gameData.put("players/" + playerId, "X");

        dbRef.child("games").child(gameId).setValue(gameData)
                .addOnSuccessListener(aVoid -> {
                    activity.runOnUiThread(() -> activity.showToast("Game created. Waiting for opponent..."));
                    setupGameListeners();
                })
                .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to create game")));
    }

    public void joinOnlineGame(String gameId) {
        this.gameId = gameId;
        isPlayerX = false;

        dbRef.child("games").child(gameId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && "waiting".equals(snapshot.child("status").getValue(String.class))) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("players/" + playerId, "O");
                    updates.put("status", "playing");

                    dbRef.child("games").child(gameId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                activity.runOnUiThread(() -> activity.showToast("Joined game!"));
                                setupGameListeners();
                            })
                            .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to join game")));
                } else {
                    activity.runOnUiThread(() -> activity.showToast("Game unavailable or already started"));
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                activity.runOnUiThread(() -> activity.showToast("Error: " + error.getMessage()));
            }
        });
    }

    public void findAvailableGame() {
        dbRef.child("games").orderByChild("status").equalTo("waiting").limitToFirst(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                                joinOnlineGame(gameSnapshot.getKey());
                                return;
                            }
                        } else {
                            createOnlineGame();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        activity.runOnUiThread(() -> activity.showToast("Error finding game: " + error.getMessage()));
                    }
                });
    }

    private void setupGameListeners() {
        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                GameManager gameState = snapshot.getValue(GameManager.class);
                if (gameState != null) {
                    activity.runOnUiThread(() -> {
                        updateLocalGame(gameState);
                        updateTurnStatus(gameState);
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                activity.runOnUiThread(() -> activity.showToast("Database error: " + error.getMessage()));
            }
        });
    }

    private void updateLocalGame(GameManager remoteState) {
        gameEngine.syncWithRemote(remoteState);
        activity.updateBoardState();
        if (remoteState.status.equals("game_over")) {
            activity.showToast("Game Over: " + (gameEngine.getWinner() != null ? "Player " + gameEngine.getWinner() + " wins!" : "Draw"));
        }
    }

    private void updateTurnStatus(GameManager gameState) {
        String currentPlayer = gameState.currentPlayer;
        boolean isMyTurn = (isPlayerX && "X".equals(currentPlayer)) || (!isPlayerX && "O".equals(currentPlayer));
        activity.setMyTurn(isMyTurn);
        if (isMyTurn) {
            activity.showToast("Your turn!");
        } else {
            activity.showToast("Opponent's turn");
        }
    }

    public void sendMove(int row, int col) {
        if (gameId != null) {
            String cellKey = row + "_" + col;
            Map<String, Object> updates = new HashMap<>();
            updates.put("board/" + cellKey, gameEngine.getCurrentPlayer());
            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");

            dbRef.child("games").child(gameId).updateChildren(updates)
                    .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to send move")));
        }
    }

    public void updateDiceState(int[] dice, String currentCombo) {
        if (gameId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("dice", dice);
            updates.put("currentCombo", currentCombo);
            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");

            dbRef.child("games").child(gameId).updateChildren(updates)
                    .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to update dice")));
        }
    }

    public void endGame() {
        if (gameId != null) {
            dbRef.child("games").child(gameId).child("status").setValue("game_over");
        }
    }

    public String getGameId() {
        return gameId;
    }
}









//package com.cookingit.dicetactoe.firebase;
//
//import android.util.Log;
//import com.cookingit.dicetactoe.GameEngine;
//import com.cookingit.dicetactoe.MainActivity;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//import java.util.HashMap;
//import java.util.Map;
//
//public class FirebaseManager {
//    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
//    private final GameEngine gameEngine;
//    private final MainActivity activity;
//    private String gameId;
//    private final String playerId;
//    private boolean isPlayerX;
//
//    public FirebaseManager(MainActivity activity, GameEngine gameEngine) {
//        this.activity = activity;
//        this.gameEngine = gameEngine;
//        this.playerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
//    }
//
//    public void createOnlineGame() {
//        gameId = dbRef.child("games").push().getKey();
//        isPlayerX = true;
//
//        Map<String, Object> gameData = new HashMap<>();
//        gameData.put("status", "waiting");
//        gameData.put("currentPlayer", "X");
//        gameData.put("players/" + playerId, "X");
//
//        dbRef.child("games").child(gameId).setValue(gameData)
//                .addOnSuccessListener(aVoid -> {
//                    activity.runOnUiThread(() -> activity.showToast("Game created. Waiting for opponent..."));
//                    setupGameListeners();
//                })
//                .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to create game")));
//    }
//
//    public void joinOnlineGame(String gameId) {
//        this.gameId = gameId;
//        isPlayerX = false;
//
//        dbRef.child("games").child(gameId).addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot snapshot) {
//                if (snapshot.exists() && "waiting".equals(snapshot.child("status").getValue(String.class))) {
//                    Map<String, Object> updates = new HashMap<>();
//                    updates.put("players/" + playerId, "O");
//                    updates.put("status", "playing");
//
//                    dbRef.child("games").child(gameId).updateChildren(updates)
//                            .addOnSuccessListener(aVoid -> {
//                                activity.runOnUiThread(() -> activity.showToast("Joined game!"));
//                                setupGameListeners();
//                            })
//                            .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to join game")));
//                } else {
//                    activity.runOnUiThread(() -> activity.showToast("Game unavailable or already started"));
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                activity.runOnUiThread(() -> activity.showToast("Error: " + error.getMessage()));
//            }
//        });
//    }
//
//    public void findAvailableGame() {
//        dbRef.child("games").orderByChild("status").equalTo("waiting").limitToFirst(1)
//                .addListenerForSingleValueEvent(new ValueEventListener() {
//                    @Override
//                    public void onDataChange(DataSnapshot snapshot) {
//                        if (snapshot.exists()) {
//                            for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
//                                joinOnlineGame(gameSnapshot.getKey());
//                                return;
//                            }
//                        } else {
//                            createOnlineGame();
//                        }
//                    }
//
//                    @Override
//                    public void onCancelled(DatabaseError error) {
//                        activity.runOnUiThread(() -> activity.showToast("Error finding game: " + error.getMessage()));
//                    }
//                });
//    }
//
//    private void setupGameListeners() {
//        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot snapshot) {
//                GameManager gameState = snapshot.getValue(GameManager.class);
//                if (gameState != null) {
//                    activity.runOnUiThread(() -> {
//                        updateLocalGame(gameState);
//                        updateTurnStatus(gameState);
//                    });
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                activity.runOnUiThread(() -> activity.showToast("Database error: " + error.getMessage()));
//            }
//        });
//    }
//
//    private void updateLocalGame(GameManager remoteState) {
//        gameEngine.syncWithRemote(remoteState);
//        activity.updateBoardState();
//        if (remoteState.status.equals("game_over")) {
//            activity.showToast("Game Over: " + (gameEngine.getWinner() != null ? "Player " + gameEngine.getWinner() + " wins!" : "Draw"));
//        }
//    }
//
//    private void updateTurnStatus(GameManager gameState) {
//        String currentPlayer = gameState.currentPlayer;
//        boolean isMyTurn = (isPlayerX && "X".equals(currentPlayer)) || (!isPlayerX && "O".equals(currentPlayer));
//        activity.setMyTurn(isMyTurn);
//        if (isMyTurn) {
//            activity.showToast("Your turn!");
//        } else {
//            activity.showToast("Opponent's turn");
//        }
//    }
//
//    public void sendMove(int row, int col) {
//        if (gameId != null) {
//            String cellKey = row + "_" + col;
//            Map<String, Object> updates = new HashMap<>();
//            updates.put("board/" + cellKey, gameEngine.getCurrentPlayer());
//            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");
//
//            dbRef.child("games").child(gameId).updateChildren(updates)
//                    .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to send move")));
//        }
//    }
//
//    public void updateDiceState(int[] dice, String currentCombo) {
//        if (gameId != null) {
//            Map<String, Object> updates = new HashMap<>();
//            updates.put("dice", dice);
//            updates.put("currentCombo", currentCombo);
//            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");
//
//            dbRef.child("games").child(gameId).updateChildren(updates)
//                    .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to update dice")));
//        }
//    }
//
//    public void endGame() {
//        if (gameId != null) {
//            dbRef.child("games").child(gameId).child("status").setValue("game_over");
//        }
//    }
//
//    public String getGameId() {
//        return gameId;
//    }
//}










//package com.cookingit.dicetactoe.firebase;
//
////import com.google.firebase.database.*;
////import com.google
//
//import com.cookingit.dicetactoe.GameEngine;
//import com.cookingit.dicetactoe.MainActivity;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//import com.google.firebase.database.*;
//
//
//public class FirebaseManager {
//    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
//    private final GameEngine gameEngine;
//    private final MainActivity activity;
//    private String gameId;
//    private final String playerId;
//
//    public FirebaseManager(MainActivity activity, GameEngine gameEngine) {
//        this.activity = activity;
//        this.gameEngine = gameEngine;
//        this.playerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
//        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
//        /*if (currentUser != null) {
//            this.playerId = currentUser.getUid();
//        } else {
//            // Handle unauthenticated state
//            activity.startLoginActivity(); // Example: Redirect to login
//            throw new IllegalStateException("User not authenticated");
//        }*/
//    }
//
//    public void createOnlineGame() {
//        gameId = dbRef.child("games").push().getKey();
//        dbRef.child("games").child(gameId).child("players").child(playerId).setValue(true);
//        dbRef.child("games").child(gameId).child("status").setValue("waiting");
//        setupGameListeners();
//    }
//
//    public void joinOnlineGame(String gameId) {
//        this.gameId = gameId;
//        dbRef.child("games").child(gameId).child("players").child(playerId).setValue(true);
//        setupGameListeners();
//    }
//
//    private void setupGameListeners() {
//        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot snapshot) {
//                GameManager gameState = snapshot.getValue(GameManager.class);
//                if (gameState != null) {
//                    activity.runOnUiThread(() -> updateLocalGame(gameState));
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                // Handle error
//            }
//        });
//    }
//
//    private void updateLocalGame(GameManager remoteState) {
//        // Update game engine state from Firebase
//        gameEngine.syncWithRemote(remoteState);
//        activity.updateBoardState();
//    }
//
//    public void sendMove(int row, int col) {
//        if (gameId != null) {
//            dbRef.child("games").child(gameId).child("board").child(row+"_"+col)
//                    .setValue(gameEngine.getCurrentPlayer());
//        }
//    }
//
//    public void updateDiceState(int[] dice, String currentCombo) {
//        dbRef.child("games").child(gameId).child("dice").setValue(dice);
//        dbRef.child("games").child(gameId).child("currentCombo").setValue(currentCombo);
//    }
//}
