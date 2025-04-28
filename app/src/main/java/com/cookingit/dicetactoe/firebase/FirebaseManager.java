package com.cookingit.dicetactoe.firebase;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cookingit.dicetactoe.GameEngine;
import com.cookingit.dicetactoe.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
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
    private boolean lastIsMyTurn = false;
    private int joinRetryCount = 0;
    private static final int MAX_JOIN_RETRIES = 3;
    private ValueEventListener gameListener;
    private DatabaseReference gameRef;
    private boolean isLeavingGame = false;

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

    public String getGameId() {
        return gameId;
    }

    public void findOrCreateGame() {
        if (joinRetryCount >= MAX_JOIN_RETRIES) {
            Log.w("FirebaseManager", "Max retries reached, creating a new game");
            joinRetryCount = 0;
            createGame();
            return;
        }

        // Look for games waiting for players
        dbRef.child("games")
                .orderByChild("status")
                .equalTo("waiting")
                .limitToFirst(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DataSnapshot snapshot = task.getResult();
                        if (snapshot.exists()) {
                            boolean gameFound = false;
                            for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                                String gameId = gameSnapshot.getKey();
                                joinRetryCount++;
                                joinGame(gameId);
                                gameFound = true;
                                break;
                            }
                            if (!gameFound) {
                                createGame();
                            }
                        } else {
                            createGame();
                        }
                    } else {
                        Log.e("FirebaseManager", "Failed to find game: " + task.getException().getMessage());
                        activity.runOnUiThread(() -> activity.showToast("Failed to find game: " + task.getException().getMessage()));
                        createGame();
                    }
                });
    }

    public void joinGame(String gameId) {
        this.gameId = gameId;
        this.isPlayerX = false;

        DatabaseReference gameRef = dbRef.child("games").child(gameId);
        gameRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                try {
                    GameManager game = mutableData.getValue(GameManager.class);
                    if (game == null || !"waiting".equals(game.status) || game.players.size() >= 2) {
                        return Transaction.abort();
                    }

                    // Update game status
                    game.status = "playing";
                    game.players.put(playerId, "O");

                    // Add last active timestamps for both players
                    if (game.playerTimestamps == null) {
                        game.playerTimestamps = new HashMap<>();
                    }

                    // Set timestamp for player O (current player)
                    game.playerTimestamps.put(playerId, ServerValue.TIMESTAMP);

                    // Find player X's ID and update their timestamp too
                    for (Map.Entry<String, String> entry : game.players.entrySet()) {
                        if ("X".equals(entry.getValue()) && !playerId.equals(entry.getKey())) {
                            game.playerTimestamps.put(entry.getKey(), ServerValue.TIMESTAMP);
                            break;
                        }
                    }

                    mutableData.setValue(game);
                    return Transaction.success(mutableData);
                } catch (Exception e) {
                    Log.e("FirebaseManager", "Deserialization error: " + e.getMessage());
                    return Transaction.abort();
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                if (error != null) {
                    Log.e("FirebaseManager", "Transaction failed: " + error.getMessage());
                    activity.runOnUiThread(() -> activity.showToast("Failed to join game: " + error.getMessage()));
                    findOrCreateGame();
                    return;
                }
                if (committed) {
                    Log.d("FirebaseManager", "Joined game successfully: " + gameId);
                    activity.runOnUiThread(() -> activity.showToast("Joined game!"));
                    listenToGame(gameId);
                    activity.setMyTurn(false);

                    // Start heartbeat to keep player active status
                    startPlayerHeartbeat();
                } else {
                    Log.w("FirebaseManager", "Transaction aborted, game full or invalid");
                    activity.runOnUiThread(() -> activity.showToast("Game unavailable, trying another..."));
                    findOrCreateGame();
                }
            }
        });
    }

    public void createGame() {
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("status", "waiting");
        gameData.put("currentPlayer", "X");
        gameData.put("currentCombo", "");
        gameData.put("createdAt", ServerValue.TIMESTAMP);

        // Convert dice list to a map with string keys
        Map<String, Integer> diceMap = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            diceMap.put(String.valueOf(i), 0);
        }
        gameData.put("dice", diceMap);

        Map<String, String> players = new HashMap<>();
        players.put(playerId, "X");
        gameData.put("players", players);

        // Initialize player timestamps
        Map<String, Object> playerTimestamps = new HashMap<>();
        playerTimestamps.put(playerId, ServerValue.TIMESTAMP);
        gameData.put("playerTimestamps", playerTimestamps);

        // Initialize an empty board map (without explicitly setting null values)
        Map<String, String> boardMap = new HashMap<>();
        gameData.put("board", boardMap);

        Log.d("FirebaseManager", "Creating game with simple data structure: " + gameData);

        DatabaseReference newGameRef = dbRef.child("games").push();
        gameId = newGameRef.getKey();

        newGameRef.setValue(gameData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("FirebaseManager", "Game created: " + gameId);
                isPlayerX = true;
                listenToGame(gameId);
                activity.setMyTurn(true);

                // Start heartbeat to keep player active status
                startPlayerHeartbeat();

                activity.runOnUiThread(() -> activity.showToast("Waiting for an opponent to join..."));
            } else {
                Log.e("FirebaseManager", "Failed to create game: " + task.getException().getMessage());
                activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + task.getException().getMessage()));
            }
        });
    }

    // Heartbeat mechanism to track player activity
    private void startPlayerHeartbeat() {
        if (gameId == null) return;

        // Update player timestamp every 30 seconds
        new Thread(() -> {
            while (gameId != null && !isLeavingGame) {
                try {
                    // Update timestamp
                    dbRef.child("games").child(gameId).child("playerTimestamps").child(playerId)
                            .setValue(ServerValue.TIMESTAMP);

                    // Check for inactive opponent
                    checkForInactiveOpponent();

                    // Sleep for 30 seconds
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Log.e("FirebaseManager", "Heartbeat interrupted", e);
                    break;
                } catch (Exception e) {
                    Log.e("FirebaseManager", "Error in heartbeat", e);
                }
            }
        }).start();
    }

    private void checkForInactiveOpponent() {
        if (gameId == null) return;

        // Get timestamps of all players
        dbRef.child("games").child(gameId).child("playerTimestamps").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DataSnapshot snapshot = task.getResult();
                long currentTime = System.currentTimeMillis();

                for (DataSnapshot playerSnapshot : snapshot.getChildren()) {
                    String playerKey = playerSnapshot.getKey();
                    if (playerKey != null && !playerKey.equals(playerId)) {
                        Long lastSeen = playerSnapshot.getValue(Long.class);
                        if (lastSeen != null) {
                            // If player hasn't updated in 2 minutes (120000ms), consider them inactive
                            if (currentTime - lastSeen > 120000) {
                                handleInactiveOpponent(playerKey);
                            }
                        }
                    }
                }
            }
        });
    }

    private void handleInactiveOpponent(String inactivePlayerId) {
        // Mark game as ended due to player inactivity
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "player_left");
        updates.put("leftPlayer", inactivePlayerId);

        dbRef.child("games").child(gameId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseManager", "Opponent marked as inactive: " + inactivePlayerId);
                    activity.runOnUiThread(() -> activity.showToast("Opponent left the game."));
                });
    }

    public void listenToGame(String gameId) {
        // Remove previous listener if exists
        if (gameRef != null && gameListener != null) {
            gameRef.removeEventListener(gameListener);
        }

        gameRef = dbRef.child("games").child(gameId);
        gameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    Log.d("FirebaseManager", "Received snapshot: " + snapshot.getValue());
                    GameManager gameState = new GameManager();

                    // Set the basic properties
                    gameState.currentPlayer = snapshot.child("currentPlayer").getValue(String.class);
                    gameState.status = snapshot.child("status").getValue(String.class);
                    gameState.currentCombo = snapshot.child("currentCombo").getValue(String.class);

                    // Parse board
                    Map<String, String> board = new HashMap<>();
                    for (DataSnapshot child : snapshot.child("board").getChildren()) {
                        String key = child.getKey();
                        String value = child.getValue(String.class);
                        if (key != null && value != null) {
                            board.put(key, value);
                        }
                    }
                    gameState.board = board;

                    // Parse players
                    Map<String, String> players = new HashMap<>();
                    for (DataSnapshot child : snapshot.child("players").getChildren()) {
                        String key = child.getKey();
                        String value = child.getValue(String.class);
                        if (key != null && value != null) {
                            players.put(key, value);
                        }
                    }
                    gameState.players = players;

                    // Handle dice which might be in different formats
                    gameState.dice = snapshot.child("dice").getValue();

                    // Check if game status is "player_left"
                    if ("player_left".equals(gameState.status)) {
                        String leftPlayer = snapshot.child("leftPlayer").getValue(String.class);
                        if (leftPlayer != null && !leftPlayer.equals(playerId)) {
                            activity.runOnUiThread(() -> {
                                activity.showToast("Your opponent left the game. You win!");
                                activity.displayGameLeftDialog(true);
                            });
                        }
                    }

                    if (gameState != null) {
                        Log.d("FirebaseManager", "Deserialized gameState: board=" + gameState.board + ", status=" + gameState.status);
                        activity.runOnUiThread(() -> {
                            updateLocalGame(gameState);
                            updateTurnStatus(gameState);
                        });
                    } else {
                        Log.w("FirebaseManager", "Game state is null, skipping update");
                    }
                } catch (Exception e) {
                    Log.e("FirebaseManager", "Error deserializing data", e);
                    activity.runOnUiThread(() -> activity.showToast("Error parsing game data"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseManager", "Database error: " + error.getMessage());
                activity.runOnUiThread(() -> activity.showToast("Database error: " + error.getMessage()));
            }
        };

        gameRef.addValueEventListener(gameListener);
    }

    private void updateLocalGame(GameManager remoteState) {
        if (remoteState.board == null) {
            remoteState.board = new HashMap<>(); // Initialize empty board if null
            Log.w("FirebaseManager", "Remote board was null, initialized to empty map");
        }
        gameEngine.syncWithRemote(remoteState);
        activity.updateBoardState();
        if ("game_over".equals(remoteState.status)) {
            activity.showToast("Game Over: " + (gameEngine.getWinner() != null ? "Player " + gameEngine.getWinner() + " wins!" : "Draw"));
        }
    }

    private void updateTurnStatus(GameManager gameState) {
        String currentPlayer = gameState.currentPlayer;

        // Determine player symbol by player ID
        String mySymbol = null;
        for (Map.Entry<String, String> entry : gameState.players.entrySet()) {
            if (entry.getKey().equals(playerId)) {
                mySymbol = entry.getValue();
                break;
            }
        }

        if (mySymbol == null) {
            Log.e("FirebaseManager", "Player symbol not found in game");
            return;
        }

        boolean isMyTurn = mySymbol.equals(currentPlayer);
        if (isMyTurn != lastIsMyTurn) {
            activity.setMyTurn(isMyTurn);
            if (isMyTurn) {
                activity.showToast("Your turn!");
            }
            lastIsMyTurn = isMyTurn;
        }
    }

    public void sendMove(int row, int col) {
        if (gameId != null) {
            Map<String, String> updatedBoard = gameEngine.getBoardAsMap();
            if (row >= 0 && col >= 0) {
                updatedBoard.put(row + "_" + col, gameEngine.getCurrentPlayer());
            }
            Log.d("FirebaseManager", "Sending move: row=" + row + ", col=" + col + ", board=" + updatedBoard);

            Map<String, Object> updates = new HashMap<>();
            updates.put("board", updatedBoard);
            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");

            Map<String, Integer> diceMap = new HashMap<>();
            for (int i = 0; i < 5; i++) {
                diceMap.put(String.valueOf(i), 0);
            }
            updates.put("dice", diceMap);

            updates.put("currentCombo", "");

            // Update player timestamp
            dbRef.child("games").child(gameId).child("playerTimestamps").child(playerId)
                    .setValue(ServerValue.TIMESTAMP);

            dbRef.child("games").child(gameId).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> Log.d("FirebaseManager", "Move sent successfully"))
                    .addOnFailureListener(e -> {
                        Log.e("FirebaseManager", "Failed to send move: " + e.getMessage());
                        activity.runOnUiThread(() -> activity.showToast("Failed to send move: " + e.getMessage()));
                    });
        }
    }

    public void updateDiceState(List<Integer> dice, String currentCombo) {
        if (gameId != null) {
            try {
                Map<String, Object> updates = new HashMap<>();

                // Convert List to Map for Firebase, using Object instead of Integer
                Map<String, Object> diceMap = new HashMap<>();
                for (int i = 0; i < dice.size(); i++) {
                    diceMap.put(String.valueOf(i), dice.get(i));
                }

                updates.put("dice", diceMap);
                updates.put("currentCombo", currentCombo != null ? currentCombo : "");

                // Update player timestamp
                dbRef.child("games").child(gameId).child("playerTimestamps").child(playerId)
                        .setValue(ServerValue.TIMESTAMP);

                dbRef.child("games").child(gameId).updateChildren(updates)
                        .addOnSuccessListener(aVoid ->
                                Log.d("FirebaseManager", "Dice state updated successfully"))
                        .addOnFailureListener(e -> {
                            Log.e("FirebaseManager", "Failed to update dice: " + e.getMessage(), e);
                            activity.runOnUiThread(() ->
                                    activity.showToast("Failed to update dice: " + e.getMessage()));
                        });
            } catch (Exception e) {
                Log.e("FirebaseManager", "Exception updating dice state", e);
                activity.runOnUiThread(() ->
                        activity.showToast("Error updating dice state: " + e.getMessage()));
            }
        }
    }

    public void endGame() {
        if (gameId != null) {
            dbRef.child("games").child(gameId).child("status").setValue("game_over");
        }
    }

    public void leaveGame() {
        if (gameId == null) return;

        isLeavingGame = true;

        // Update game status to indicate player left
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "player_left");
        updates.put("leftPlayer", playerId);

        // Get the reference to remove listener after updating
        DatabaseReference gameToLeave = dbRef.child("games").child(gameId);

        // Remove listener first to avoid callbacks during cleanup
        if (gameListener != null) {
            gameToLeave.removeEventListener(gameListener);
            gameListener = null;
        }

        // Update the game status
        gameToLeave.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseManager", "Successfully left game: " + gameId);
                    gameId = null; // Clear game ID
                    gameRef = null;
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseManager", "Failed to leave game: " + e.getMessage());
                    isLeavingGame = false;
                });
    }

    public void cleanup() {
        // Remove all listeners
        if (gameRef != null && gameListener != null) {
            gameRef.removeEventListener(gameListener);
            gameListener = null;
        }

        // If we're in a game, mark that we've left
        if (gameId != null && !isLeavingGame) {
            leaveGame();
        }
    }
}









//package com.cookingit.dicetactoe.firebase;
//
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//
//import com.cookingit.dicetactoe.GameEngine;
//import com.cookingit.dicetactoe.MainActivity;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.MutableData;
//import com.google.firebase.database.Transaction;
//import com.google.firebase.database.ValueEventListener;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class FirebaseManager {
//    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
//    private final GameEngine gameEngine;
//    private final MainActivity activity;
//    private String gameId;
//    private final String playerId;
//    private boolean isPlayerX;
//    private boolean lastIsMyTurn = false;
//    private int joinRetryCount = 0;
//    private static final int MAX_JOIN_RETRIES = 3;
//
//
//
//    public FirebaseManager(MainActivity activity, GameEngine gameEngine) {
//        this.activity = activity;
//        this.gameEngine = gameEngine;
//        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//        if (user == null) {
//            throw new IllegalStateException("No authenticated user found. Please sign in first.");
//        }
//        this.playerId = user.getUid();
//    }
//
//    public DatabaseReference getDbRef() {
//        return dbRef;
//    }
//
//    public String getGameId() {
//        return gameId;
//    }
//
//    public void findOrCreateGame() {
//        if (joinRetryCount >= MAX_JOIN_RETRIES) {
//            Log.w("FirebaseManager", "Max retries reached, creating a new game");
//            joinRetryCount = 0;
//            createGame();
//            return;
//        }
//
//        dbRef.child("games").orderByChild("status").equalTo("waiting").limitToFirst(1).get().addOnCompleteListener(task -> {
//            if (task.isSuccessful()) {
//                DataSnapshot snapshot = task.getResult();
//                if (snapshot.exists()) {
//                    for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
//                        String gameId = gameSnapshot.getKey();
//                        joinRetryCount++;
//                        joinGame(gameId);
//                        return;
//                    }
//                }
//                joinRetryCount = 0;
//                createGame();
//            } else {
//                Log.e("FirebaseManager", "Failed to find game: " + task.getException().getMessage());
//                activity.runOnUiThread(() -> activity.showToast("Failed to find game: " + task.getException().getMessage()));
//                joinRetryCount = 0;
//                createGame();
//            }
//        });
//    }
//
//    public void joinGame(String gameId) {
//        this.gameId = gameId;
//        this.isPlayerX = false;
//
//        DatabaseReference gameRef = dbRef.child("games").child(gameId);
//        gameRef.runTransaction(new Transaction.Handler() {
//            @NonNull
//            @Override
//            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
//                try {
//                    GameManager game = mutableData.getValue(GameManager.class);
//                    if (game == null || !"waiting".equals(game.status) || game.players.size() >= 2) {
//                        return Transaction.abort();
//                    }
//                    game.status = "playing";
//                    game.players.put("player2", "O");
//                    mutableData.setValue(game);
//                    return Transaction.success(mutableData);
//                } catch (Exception e) {
//                    Log.e("FirebaseManager", "Deserialization error: " + e.getMessage());
//                    return Transaction.abort();
//                }
//            }
//
//            @Override
//            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
//                if (error != null) {
//                    Log.e("FirebaseManager", "Transaction failed: " + error.getMessage());
//                    activity.runOnUiThread(() -> activity.showToast("Failed to join game: " + error.getMessage()));
//                    findOrCreateGame();
//                    return;
//                }
//                if (committed) {
//                    Log.d("FirebaseManager", "Joined game successfully: " + gameId);
//                    activity.runOnUiThread(() -> activity.showToast("Joined game!"));
//                    listenToGame(gameId);
//                    activity.setMyTurn(false);
//                } else {
//                    Log.w("FirebaseManager", "Transaction aborted, game full or invalid");
//                    activity.runOnUiThread(() -> activity.showToast("Game unavailable, trying another..."));
//                    findOrCreateGame();
//                }
//            }
//        });
//    }
//
//    public void createGame() {
//        Map<String, Object> gameData = new HashMap<>();
//        gameData.put("status", "waiting");
//        gameData.put("currentPlayer", "X");
//        gameData.put("currentCombo", "none");
//        //gameData.put("currentCombo", "");
//
//        // Convert dice list to a map with string keys
//        Map<String, Integer> diceMap = new HashMap<>();
//        for (int i = 0; i < 5; i++) {
//            diceMap.put(String.valueOf(i), 0);
//        }
//        gameData.put("dice", diceMap);
//
//        Map<String, String> players = new HashMap<>();
//        players.put("player1", "X");
//        gameData.put("players", players);
//
//        // Initialize an empty board map (without explicitly setting null values)
//        Map<String, String> boardMap = new HashMap<>();
//        gameData.put("board", boardMap);
//
//        Log.d("FirebaseManager", "Creating game with simple data structure: " + gameData);
//
//        DatabaseReference newGameRef = dbRef.child("games").push();
//        gameId = newGameRef.getKey();
//
//        newGameRef.setValue(gameData).addOnCompleteListener(task -> {
//            if (task.isSuccessful()) {
//                Log.d("FirebaseManager", "Game created: " + gameId);
//                isPlayerX = true;
//                listenToGame(gameId);
//                activity.setMyTurn(true);
//            } else {
//                Log.e("FirebaseManager", "Failed to create game: " + task.getException().getMessage());
//                activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + task.getException().getMessage()));
//            }
//        });
//    }
//
//    public void listenToGame(String gameId) {
//        dbRef.child("games").child(gameId).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                try {
//                    Log.d("FirebaseManager", "Received snapshot: " + snapshot.getValue());
//                    GameManager gameState = new GameManager();
//
//                    // Set the basic properties
//                    gameState.currentPlayer = snapshot.child("currentPlayer").getValue(String.class);
//                    gameState.status = snapshot.child("status").getValue(String.class);
//                    gameState.currentCombo = snapshot.child("currentCombo").getValue(String.class);
//
//                    // Parse board
//                    Map<String, String> board = new HashMap<>();
//                    for (DataSnapshot child : snapshot.child("board").getChildren()) {
//                        String key = child.getKey();
//                        String value = child.getValue(String.class);
//                        if (key != null && value != null) {
//                            board.put(key, value);
//                        }
//                    }
//                    gameState.board = board;
//
//                    // Parse players
//                    Map<String, String> players = new HashMap<>();
//                    for (DataSnapshot child : snapshot.child("players").getChildren()) {
//                        String key = child.getKey();
//                        String value = child.getValue(String.class);
//                        if (key != null && value != null) {
//                            players.put(key, value);
//                        }
//                    }
//                    gameState.players = players;
//
//                    // Handle dice which might be in different formats
//                    gameState.dice = snapshot.child("dice").getValue();
//
//                    if (gameState != null) {
//                        Log.d("FirebaseManager", "Deserialized gameState: board=" + gameState.board + ", status=" + gameState.status);
//                        activity.runOnUiThread(() -> {
//                            updateLocalGame(gameState);
//                            updateTurnStatus(gameState);
//                        });
//                    } else {
//                        Log.w("FirebaseManager", "Game state is null, skipping update");
//                    }
//                } catch (Exception e) {
//                    Log.e("FirebaseManager", "Error deserializing data", e);
//                    activity.runOnUiThread(() -> activity.showToast("Error parsing game data"));
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e("FirebaseManager", "Database error: " + error.getMessage());
//                activity.runOnUiThread(() -> activity.showToast("Database error: " + error.getMessage()));
//            }
//        });
//    }
//
//    private void updateLocalGame(GameManager remoteState) {
//        if (remoteState.board == null) {
//            remoteState.board = new HashMap<>(); // Initialize empty board if null
//            Log.w("FirebaseManager", "Remote board was null, initialized to empty map");
//        }
//        gameEngine.syncWithRemote(remoteState);
//        activity.updateBoardState();
//        if ("game_over".equals(remoteState.status)) {
//            activity.showToast("Game Over: " + (gameEngine.getWinner() != null ? "Player " + gameEngine.getWinner() + " wins!" : "Draw"));
//        }
//    }
//
//    private void updateTurnStatus(GameManager gameState) {
//        String currentPlayer = gameState.currentPlayer;
//        Map<String, String> players = gameState.getPlayers();
//        String mySymbol = isPlayerX ? "X" : "O";
//        boolean isMyTurn = mySymbol.equals(currentPlayer);
//        if (isMyTurn != lastIsMyTurn) {
//            activity.setMyTurn(isMyTurn);
//            activity.showToast(isMyTurn ? "Your turn!" : "Opponent's turn");
//            lastIsMyTurn = isMyTurn;
//        }
//    }
//
//    public void sendMove(int row, int col) {
//        if (gameId != null) {
//            Map<String, String> updatedBoard = gameEngine.getBoardAsMap();
//            updatedBoard.put(row + "_" + col, gameEngine.getCurrentPlayer());
//            Log.d("FirebaseManager", "Sending move: row=" + row + ", col=" + col + ", board=" + updatedBoard);
//
//            Map<String, Object> updates = new HashMap<>();
//            updates.put("board", updatedBoard);
//            updates.put("currentPlayer", gameEngine.getCurrentPlayer().equals("X") ? "O" : "X");
//
//            Map<String, Integer> diceMap = new HashMap<>();
//            for (int i = 0; i < 5; i++) {
//                diceMap.put(String.valueOf(i), 0);
//            }
//            updates.put("dice", diceMap);
//
//            updates.put("currentCombo", "");
//
//            dbRef.child("games").child(gameId).updateChildren(updates)
//                    .addOnSuccessListener(aVoid -> Log.d("FirebaseManager", "Move sent successfully"))
//                    .addOnFailureListener(e -> {
//                        Log.e("FirebaseManager", "Failed to send move: " + e.getMessage());
//                        activity.runOnUiThread(() -> activity.showToast("Failed to send move: " + e.getMessage()));
//                    });
//        }
//    }
//
//    public void updateDiceState(List<Integer> dice, String currentCombo) {
//        if (gameId != null) {
//            try {
//                Map<String, Object> updates = new HashMap<>();
//
//                // Convert List to Map for Firebase, using Object instead of Integer
//                Map<String, Object> diceMap = new HashMap<>();
//                for (int i = 0; i < dice.size(); i++) {
//                    diceMap.put(String.valueOf(i), dice.get(i));
//                }
//
//                updates.put("dice", diceMap);
//                updates.put("currentCombo", currentCombo != null ? currentCombo : "");
//
//                dbRef.child("games").child(gameId).updateChildren(updates)
//                        .addOnSuccessListener(aVoid ->
//                                Log.d("FirebaseManager", "Dice state updated successfully"))
//                        .addOnFailureListener(e -> {
//                            Log.e("FirebaseManager", "Failed to update dice: " + e.getMessage(), e);
//                            activity.runOnUiThread(() ->
//                                    activity.showToast("Failed to update dice: " + e.getMessage()));
//                        });
//            } catch (Exception e) {
//                Log.e("FirebaseManager", "Exception updating dice state", e);
//                activity.runOnUiThread(() ->
//                        activity.showToast("Error updating dice state: " + e.getMessage()));
//            }
//        }
//    }
//
//    public void endGame() {
//        if (gameId != null) {
//            dbRef.child("games").child(gameId).child("status").setValue("game_over");
//        }
//    }
//}