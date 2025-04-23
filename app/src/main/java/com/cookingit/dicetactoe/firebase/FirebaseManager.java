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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    public DatabaseReference getDbRef() {
        return dbRef;
    }

    public void createOnlineGame() {
        gameId = dbRef.child("games").push().getKey();
        isPlayerX = true;

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("status", "waiting");
        gameData.put("currentPlayer", "X");
        gameData.put("dice", new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0)));
        gameData.put("currentCombo", "");
        Map<String, String> players = new HashMap<>();
        players.put(playerId, "X");
        gameData.put("players", players);
        gameData.put("board", new HashMap<String, String>()); // Empty board as Map

        Log.d("FirebaseManager", "Creating game with data: " + gameData);

        dbRef.child("games").child(gameId).setValue(gameData)
                .addOnSuccessListener(aVoid -> {
                    activity.runOnUiThread(() -> activity.showToast("Game created. Waiting for opponent..."));
                    setupGameListeners();
                })
                .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + e.getMessage())));
    }

//    public void createOnlineGame() {
//        gameId = dbRef.child("games").push().getKey();
//        isPlayerX = true;
//
//        // Initialize a 3x3 board as List<List<String>>
//        List<List<String>> board = new ArrayList<>();
//        for (int i = 0; i < 3; i++) {
//            List<String> row = new ArrayList<>();
//            for (int j = 0; j < 3; j++) {
//                row.add(null);
//            }
//            board.add(row);
//        }
//
//        Map<String, Object> gameData = new HashMap<>();
//        gameData.put("status", "waiting");
//        gameData.put("currentPlayer", "X");
//        gameData.put("dice", new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0)));
//        gameData.put("currentCombo", "");
//        Map<String, String> players = new HashMap<>();
//        players.put(playerId, "X");
//        gameData.put("players", players);
//        gameData.put("board", board);
//
//        Log.d("FirebaseManager", "Creating game with data: " + gameData);
//
//        dbRef.child("games").child(gameId).setValue(gameData)
//                .addOnSuccessListener(aVoid -> {
//                    activity.runOnUiThread(() -> activity.showToast("Game created. Waiting for opponent..."));
//                    setupGameListeners();
//                })
//                .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + e.getMessage())));
//    }

//    public void createOnlineGame() {
//        gameId = dbRef.child("games").push().getKey();
//        isPlayerX = true;
//
//        // Initialize a 3x3 board as List<List<String>>
//        List<List<String>> board = new ArrayList<>();
//        for (int i = 0; i < 3; i++) {
//            List<String> row = new ArrayList<>();
//            for (int j = 0; j < 3; j++) {
//                row.add(null);
//            }
//            board.add(row);
//        }
//
//        Map<String, Object> gameData = new HashMap<>();
//        gameData.put("status", "waiting");
//        gameData.put("currentPlayer", "X");
//        gameData.put("dice", new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0)));
//        gameData.put("currentCombo", "");
//        Map<String, String> players = new HashMap<>();
//        players.put(playerId, "X");
//        gameData.put("players", players);
//        gameData.put("board", board); // Store as List<List<String>>
//
//        dbRef.child("games").child(gameId).setValue(gameData)
//                .addOnSuccessListener(aVoid -> {
//                    activity.runOnUiThread(() -> activity.showToast("Game created. Waiting for opponent..."));
//                    setupGameListeners();
//                })
//                .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + e.getMessage())));
//    }

//    public void createOnlineGame() {
//        gameId = dbRef.child("games").push().getKey();
//        isPlayerX = true;
//
//        Map<String, Object> gameData = new HashMap<>();
//        gameData.put("status", "waiting");
//        gameData.put("currentPlayer", "X");
//        gameData.put("dice", new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0)));
//        gameData.put("currentCombo", "");
//        Map<String, String> players = new HashMap<>();
//        players.put(playerId, "X");
//        gameData.put("players", players);
//        gameData.put("board", new HashMap<String, String>()); // Initialize empty board
//
//        dbRef.child("games").child(gameId).setValue(gameData)
//                .addOnSuccessListener(aVoid -> {
//                    activity.runOnUiThread(() -> activity.showToast("Game created. Waiting for opponent..."));
//                    setupGameListeners();
//                })
//                .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + e.getMessage())));
//    }

//    public void createOnlineGame() {
//        gameId = dbRef.child("games").push().getKey();
//        isPlayerX = true;
//
//        // Create a nested structure for players
//        Map<String, Object> gameData = new HashMap<>();
//        gameData.put("status", "waiting");
//        gameData.put("currentPlayer", "X");
//        Map<String, String> players = new HashMap<>();
//        players.put(playerId, "X");
//        gameData.put("players", players);
//
//        dbRef.child("games").child(gameId).setValue(gameData)
//                .addOnSuccessListener(aVoid -> {
//                    activity.runOnUiThread(() -> activity.showToast("Game created. Waiting for opponent..."));
//                    setupGameListeners();
//                })
//                .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + e.getMessage())));
//    }

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

    public void joinOnlineGame(String gameId) {
        this.gameId = gameId;
        isPlayerX = false;

        dbRef.child("games").child(gameId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && "waiting".equals(snapshot.child("status").getValue(String.class))) {
                    // Update the players node to add the current player as "O"
                    dbRef.child("games").child(gameId).child("players").child(playerId).setValue("O")
                            .addOnSuccessListener(aVoid -> {
                                dbRef.child("games").child(gameId).child("status").setValue("playing")
                                        .addOnSuccessListener(aVoid2 -> {
                                            activity.runOnUiThread(() -> activity.showToast("Joined game!"));
                                            setupGameListeners();
                                        })
                                        .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to update game status")));
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

    public void findAvailableGame() {
        Log.d("FirebaseManager", "Querying /games for status=waiting");
        dbRef.child("games").orderByChild("status").equalTo("waiting").limitToFirst(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Log.d("FirebaseManager", "Snapshot exists: " + snapshot.exists());
                        if (snapshot.exists()) {
                            for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                                Log.d("FirebaseManager", "Joining game: " + gameSnapshot.getKey());
                                joinOnlineGame(gameSnapshot.getKey());
                                return;
                            }
                        } else {
                            Log.d("FirebaseManager", "No available games, creating new game");
                            createOnlineGame();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e("FirebaseManager", "Error querying games: " + error.getMessage());
                        activity.runOnUiThread(() -> activity.showToast("Error finding game: " + error.getMessage()));
                    }
                });
    }

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

    private void setupGameListeners() {
        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.d("FirebaseManager", "Received snapshot: " + snapshot.getValue());
                GameManager gameState = snapshot.getValue(GameManager.class);
                if (gameState != null) {
                    Log.d("FirebaseManager", "Deserialized gameState: board=" + gameState.board);
                    activity.runOnUiThread(() -> {
                        updateLocalGame(gameState);
                        updateTurnStatus(gameState);
                    });
                } else {
                    Log.w("FirebaseManager", "Game state is null, skipping update");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("FirebaseManager", "Database error: " + error.getMessage());
                activity.runOnUiThread(() -> activity.showToast("Database error: " + error.getMessage()));
            }
        });
    }

//    private void setupGameListeners() {
//        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot snapshot) {
//                Log.d("FirebaseManager", "Received snapshot: " + snapshot.getValue());
//                GameManager gameState = snapshot.getValue(GameManager.class);
//                if (gameState != null) {
//                    Log.d("FirebaseManager", "Deserialized gameState: board=" + gameState.board);
//                    // Ensure board is initialized if null or malformed
//                    if (gameState.board == null) {
//                        gameState.board = new ArrayList<>();
//                        for (int i = 0; i < 3; i++) {
//                            List<String> row = new ArrayList<>();
//                            for (int j = 0; j < 3; j++) {
//                                row.add(null);
//                            }
//                            gameState.board.add(row);
//                        }
//                        Log.d("FirebaseManager", "Initialized null board to: " + gameState.board);
//                    } else {
//                        // Convert HashMap or malformed board back to List<List<String>>
//                        List<List<String>> correctedBoard = new ArrayList<>();
//                        for (int i = 0; i < 3; i++) {
//                            List<String> row = new ArrayList<>();
//                            for (int j = 0; j < 3; j++) {
//                                row.add(null); // Default to null
//                            }
//                            correctedBoard.add(row);
//                        }
//                        // If board is a HashMap (Firebase converted List to Map)
//                        if (snapshot.child("board").getValue() instanceof Map) {
//                            Map<String, Object> boardMap = (Map<String, Object>) snapshot.child("board").getValue();
//                            for (String rowKey : boardMap.keySet()) {
//                                int rowIndex = Integer.parseInt(rowKey);
//                                Object rowData = boardMap.get(rowKey);
//                                if (rowData instanceof Map) {
//                                    Map<String, String> rowMap = (Map<String, String>) rowData;
//                                    for (String colKey : rowMap.keySet()) {
//                                        int colIndex = Integer.parseInt(colKey);
//                                        correctedBoard.get(rowIndex).set(colIndex, rowMap.get(colKey));
//                                    }
//                                } else if (rowData instanceof List) {
//                                    List<String> rowList = (List<String>) rowData;
//                                    for (int j = 0; j < rowList.size(); j++) {
//                                        correctedBoard.get(rowIndex).set(j, rowList.get(j));
//                                    }
//                                }
//                            }
//                        } else if (gameState.board != null) {
//                            correctedBoard = gameState.board;
//                        }
//                        gameState.board = correctedBoard;
//                        Log.d("FirebaseManager", "Corrected board to: " + gameState.board);
//                    }
//                    activity.runOnUiThread(() -> {
//                        updateLocalGame(gameState);
//                        updateTurnStatus(gameState);
//                    });
//                } else {
//                    Log.w("FirebaseManager", "Game state is null, skipping update");
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                Log.e("FirebaseManager", "Database error: " + error.getMessage());
//                activity.runOnUiThread(() -> activity.showToast("Database error: " + error.getMessage()));
//            }
//        });
//    }

//    private void setupGameListeners() {
//        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot snapshot) {
//                Log.d("FirebaseManager", "Received snapshot: " + snapshot.getValue());
//                GameManager gameState = snapshot.getValue(GameManager.class);
//                if (gameState != null) {
//                    Log.d("FirebaseManager", "Deserialized gameState: board=" + gameState.board);
//                    // Ensure board is initialized if null
//                    if (gameState.board == null) {
//                        gameState.board = new ArrayList<>();
//                        for (int i = 0; i < 3; i++) {
//                            List<String> row = new ArrayList<>();
//                            for (int j = 0; j < 3; j++) {
//                                row.add(null);
//                            }
//                            gameState.board.add(row);
//                        }
//                        Log.d("FirebaseManager", "Initialized null board to: " + gameState.board);
//                    }
//                    activity.runOnUiThread(() -> {
//                        updateLocalGame(gameState);
//                        updateTurnStatus(gameState);
//                    });
//                } else {
//                    Log.w("FirebaseManager", "Game state is null, skipping update");
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                Log.e("FirebaseManager", "Database error: " + error.getMessage());
//                activity.runOnUiThread(() -> activity.showToast("Database error: " + error.getMessage()));
//            }
//        });
//    }

//    private void setupGameListeners() {
//        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot snapshot) {
//                Log.d("FirebaseManager", "Received snapshot: " + snapshot.getValue());
//                GameManager gameState = snapshot.getValue(GameManager.class);
//                if (gameState != null) {
//                    Log.d("FirebaseManager", "Deserialized gameState: board=" + gameState.board);
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
            // Get the current board state as a Map
            Map<String, String> updatedBoard = gameEngine.getBoardAsMap();
            // Apply the new move
            updatedBoard.put(row + "_" + col, gameEngine.getCurrentPlayer());
            Log.d("FirebaseManager", "Sending move: row=" + row + ", col=" + col + ", board=" + updatedBoard);

            Map<String, Object> updates = new HashMap<>();
            updates.put("board", updatedBoard);
            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");
            updates.put("dice", new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0))); // Reset dice
            updates.put("currentCombo", ""); // Reset combo

            dbRef.child("games").child(gameId).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> Log.d("FirebaseManager", "Move sent successfully"))
                    .addOnFailureListener(e -> {
                        Log.e("FirebaseManager", "Failed to send move: " + e.getMessage());
                        activity.runOnUiThread(() -> activity.showToast("Failed to send move: " + e.getMessage()));
                    });
        }
    }

//    public void sendMove(int row, int col) {
//        if (gameId != null) {
//            // Get the current board state from the game engine
//            List<List<String>> updatedBoard = new ArrayList<>();
//            for (int i = 0; i < 3; i++) {
//                List<String> boardRow = new ArrayList<>();
//                for (int j = 0; j < 3; j++) {
//                    boardRow.add(gameEngine.getCellValue(i, j));
//                }
//                updatedBoard.add(boardRow);
//            }
//            // Apply the new move
//            updatedBoard.get(row).set(col, gameEngine.getCurrentPlayer());
//            Log.d("FirebaseManager", "Sending move: row=" + row + ", col=" + col + ", board=" + updatedBoard);
//
//            Map<String, Object> updates = new HashMap<>();
//            updates.put("board", updatedBoard);
//            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");
//            updates.put("dice", new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0))); // Reset dice
//            updates.put("currentCombo", ""); // Reset combo
//
//            dbRef.child("games").child(gameId).updateChildren(updates)
//                    .addOnSuccessListener(aVoid -> Log.d("FirebaseManager", "Move sent successfully"))
//                    .addOnFailureListener(e -> {
//                        Log.e("FirebaseManager", "Failed to send move: " + e.getMessage());
//                        activity.runOnUiThread(() -> activity.showToast("Failed to send move: " + e.getMessage()));
//                    });
//
////            dbRef.child("games").child(gameId).updateChildren(updates)
////                    .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to send move: " + e.getMessage())));
//        }
//    }

//    public void sendMove(int row, int col) {
//        if (gameId != null) {
//            String cellKey = row + "_" + col;
//            Map<String, Object> updates = new HashMap<>();
//            updates.put("board/" + cellKey, gameEngine.getCurrentPlayer());
//            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");
//            updates.put("dice", new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0))); // Reset dice
//            updates.put("currentCombo", ""); // Reset combo
//
//            dbRef.child("games").child(gameId).updateChildren(updates)
//                    .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to send move: " + e.getMessage())));
//        }
//    }

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

    public void updateDiceState(List<Integer> dice, String currentCombo) { // Changed parameter type
        if (gameId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("dice", dice);
            updates.put("currentCombo", currentCombo);
            //updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");

            dbRef.child("games").child(gameId).updateChildren(updates)
                    .addOnFailureListener(e -> activity.runOnUiThread(() -> activity.showToast("Failed to update dice: " + e.getMessage())));
        }
    }

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

    public void endGame() {
        if (gameId != null) {
            dbRef.child("games").child(gameId).child("status").setValue("game_over");
        }
    }

    public String getGameId() {
        return gameId;
    }
}