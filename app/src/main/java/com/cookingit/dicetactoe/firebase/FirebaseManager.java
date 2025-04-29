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

        // First, let's check if there are any games specifically waiting for this player
        dbRef.child("games")
                .orderByChild("targetOpponentId")
                .equalTo(playerId)
                .limitToFirst(1)
                .get()
                .addOnCompleteListener(targetTask -> {
                    if (targetTask.isSuccessful() && targetTask.getResult().exists()) {
                        boolean targetGameFound = false;
                        for (DataSnapshot gameSnapshot : targetTask.getResult().getChildren()) {
                            GameManager gameData = gameSnapshot.getValue(GameManager.class);
                            if (gameData != null &&
                                    "waiting".equals(gameData.status) &&
                                    gameData.pairPreserve) {

                                String gameId = gameSnapshot.getKey();
                                Log.d("FirebaseManager", "Found a paired game waiting for me: " + gameId);
                                joinGame(gameId);
                                targetGameFound = true;
                                break;
                            }
                        }

                        if (targetGameFound) {
                            return; // We found a targeted game
                        }
                    }

                    // If no targeted games, look for any waiting games
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
                                            GameManager gameData = gameSnapshot.getValue(GameManager.class);

                                            // Skip games that are waiting for a specific other player
                                            if (gameData != null &&
                                                    gameData.targetOpponentId != null &&
                                                    !gameData.targetOpponentId.equals(playerId)) {
                                                continue;
                                            }

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
                });
    }

    public void joinGame(String gameId) {
        this.gameId = gameId;

        // Before joining, get current game state to verify it's joinable
        dbRef.child("games").child(gameId).get().addOnSuccessListener(snapshot -> {
            GameManager existingGame = snapshot.getValue(GameManager.class);
            if (existingGame == null || !"waiting".equals(existingGame.status)) {
                Log.e("FirebaseManager", "Game not joinable: " +
                        (existingGame != null ? existingGame.status : "null"));
                activity.runOnUiThread(() -> activity.showToast("Game not available, trying another..."));
                findOrCreateGame();
                return;
            }

            // Check if this is a paired game targeting this player specifically
            if (existingGame.pairPreserve && existingGame.targetOpponentId != null
                    && !existingGame.targetOpponentId.equals(playerId)) {
                // This game is waiting for a specific player, but it's not us
                Log.d("FirebaseManager", "This game is waiting for a specific player: " + existingGame.targetOpponentId);
                activity.runOnUiThread(() -> activity.showToast("Game is reserved for another player"));
                findOrCreateGame();
                return;
            }

            // Determine the role that should be assigned to this player
            String creatorId = existingGame.gameCreatedBy;
            if (creatorId == null) {
                Log.e("FirebaseManager", "Game creator ID is missing");
                findOrCreateGame();
                return;
            }

            // Find what role the creator has
            String creatorRole = null;
            for (Map.Entry<String, String> entry : existingGame.players.entrySet()) {
                if (entry.getKey().equals(creatorId)) {
                    creatorRole = entry.getValue();
                    break;
                }
            }

            if (creatorRole == null) {
                Log.e("FirebaseManager", "Creator role not found");
                findOrCreateGame();
                return;
            }

            // Join with the opposite role of the creator
            final String myRole = "X".equals(creatorRole) ? "O" : "X";
            isPlayerX = "X".equals(myRole);

            Log.d("FirebaseManager", "Will join game as: " + myRole +
                    " (creator is: " + creatorRole + ")");

            // For paired games, we want to preserve the score from previous game
            if (existingGame.pairPreserve && existingGame.previousGameId != null) {
                // Try to get the scores from the previous game
                dbRef.child("games").child(existingGame.previousGameId).get()
                        .addOnSuccessListener(prevSnapshot -> {
                            GameManager prevGame = prevSnapshot.getValue(GameManager.class);
                            if (prevGame != null) {
                                // Update local scores before joining
                                gameEngine.setPlayerXScore(prevGame.playerXScore);
                                gameEngine.setPlayerOScore(prevGame.playerOScore);
                                Log.d("FirebaseManager", "Preserved scores from previous game - X: " +
                                        prevGame.playerXScore + ", O: " + prevGame.playerOScore);
                            }
                            // Now perform the join transaction
                            performJoinTransaction(gameId, myRole);
                        })
                        .addOnFailureListener(e -> {
                            // If we can't get previous game, join anyway
                            Log.e("FirebaseManager", "Failed to get previous game: " + e.getMessage());
                            performJoinTransaction(gameId, myRole);
                        });
            } else {
                // Regular join for non-paired games
                performJoinTransaction(gameId, myRole);
            }
        }).addOnFailureListener(e -> {
            Log.e("FirebaseManager", "Failed to get game state: " + e.getMessage());
            findOrCreateGame();
        });
    }

    private void performJoinTransaction(String gameId, String myRole) {
        DatabaseReference gameRef = dbRef.child("games").child(gameId);
        gameRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                try {
                    GameManager game = mutableData.getValue(GameManager.class);
                    if (game == null || !"waiting".equals(game.status)) {
                        return Transaction.abort();
                    }

                    // Set game status to playing
                    game.status = "playing";

                    // Add this player with the determined role
                    if (game.players == null) {
                        game.players = new HashMap<>();
                    }
                    game.players.put(playerId, myRole);

                    // Update timestamps
                    if (game.playerTimestamps == null) {
                        game.playerTimestamps = new HashMap<>();
                    }
                    game.playerTimestamps.put(playerId, ServerValue.TIMESTAMP);

                    // Clear pairing-specific fields now that the pairing is complete
                    // We keep pairPreserve=true to indicate this game should maintain pairing for the next game
                    game.targetOpponentId = null;

                    // Update the game state
                    mutableData.setValue(game);
                    return Transaction.success(mutableData);
                } catch (Exception e) {
                    Log.e("FirebaseManager", "Join transaction error: " + e.getMessage(), e);
                    return Transaction.abort();
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (!committed || error != null) {
                    Log.e("FirebaseManager", "Join transaction failed: " +
                            (error != null ? error.getMessage() : "aborted"));
                    findOrCreateGame();
                    return;
                }

                // Get the latest game state after successfully joining
                gameRef.get().addOnSuccessListener(dataSnapshot -> {
                    GameManager updatedGame = dataSnapshot.getValue(GameManager.class);
                    if (updatedGame != null) {
                        Log.d("FirebaseManager", "Successfully joined game as: " + myRole);

                        // Set turn status based on current player in game
                        boolean isMyTurn = myRole.equals(updatedGame.currentPlayer);
                        activity.setMyTurn(isMyTurn);
                        lastIsMyTurn = isMyTurn;

                        // Now start listening for changes
                        listenToGame(gameId);

                        // Start heartbeat
                        startPlayerHeartbeat();

                        // Update UI
                        activity.runOnUiThread(() -> {
                            activity.updateBoardState();
                            activity.updateDiceDisplay();
                            String message = "Joined game as Player " + myRole;
                            if (updatedGame.pairPreserve) {
                                message += " (continuing match)";
                            }
                            activity.showToast(message);
                        });
                    }
                });
            }
        });
    }

    private void performJoinGameTransaction(String gameId) {
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

                    // Update game status to playing
                    game.status = "playing";

                    // Always join as player O when joining another player's game
                    game.players.put(playerId, "O");

                    // Ensure we keep the creator's move if they made one
                    if (game.board == null) {
                        game.board = new HashMap<>();
                    }

                    // Add last active timestamps
                    if (game.playerTimestamps == null) {
                        game.playerTimestamps = new HashMap<>();
                    }

                    // Set timestamp for current player
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
                    Log.e("FirebaseManager", "Join transaction error: " + e.getMessage(), e);
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
                    Log.d("FirebaseManager", "Successfully joined game: " + gameId);

                    // Critical fix: Get a fresh snapshot after successful join
                    gameRef.get().addOnSuccessListener(dataSnapshot -> {
                        GameManager gameState = dataSnapshot.getValue(GameManager.class);
                        if (gameState != null) {
                            boolean foundMyPlayer = false;

                            // Find my player assignment (X or O)
                            for (Map.Entry<String, String> entry : gameState.players.entrySet()) {
                                if (entry.getKey().equals(playerId)) {
                                    isPlayerX = "X".equals(entry.getValue());
                                    foundMyPlayer = true;
                                    Log.d("FirebaseManager", "My role confirmed as: " + (isPlayerX ? "X" : "O"));
                                    break;
                                }
                            }

                            if (!foundMyPlayer) {
                                Log.e("FirebaseManager", "Could not find player in game after joining!");
                            }

                            // Now update the UI with correct state
                            activity.runOnUiThread(() -> {
                                boolean isMyTurn = (isPlayerX && "X".equals(gameState.currentPlayer)) ||
                                        (!isPlayerX && "O".equals(gameState.currentPlayer));

                                activity.setMyTurn(isMyTurn);
                                lastIsMyTurn = isMyTurn;

                                // Sync engine state with Firebase
                                gameEngine.syncWithRemote(gameState);
                                activity.updateBoardState();
                                activity.updateDiceDisplay();

                                activity.showToast("Joined game!");
                            });

                            // Now start listening for changes
                            listenToGame(gameId);

                            // Start heartbeat
                            startPlayerHeartbeat();
                        } else {
                            Log.e("FirebaseManager", "Failed to get game data after joining");
                            activity.runOnUiThread(() -> activity.showToast("Error: Could not get game data"));
                        }
                    });
                } else {
                    Log.w("FirebaseManager", "Transaction aborted, game full or invalid");
                    activity.runOnUiThread(() -> activity.showToast("Game unavailable, trying another..."));
                    findOrCreateGame();
                }
            }
        });
    }

    private void findPairedGame(String opponentId) {
        Log.d("FirebaseManager", "Looking for a paired game with opponent: " + opponentId);

        // Look for games waiting for this specific player
        dbRef.child("games")
                .orderByChild("targetOpponentId")
                .equalTo(playerId)
                .limitToFirst(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DataSnapshot snapshot = task.getResult();
                        if (snapshot.exists()) {
                            boolean gameFound = false;
                            for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                                GameManager gameData = gameSnapshot.getValue(GameManager.class);
                                if (gameData != null &&
                                        "waiting".equals(gameData.status) &&
                                        gameData.pairPreserve &&
                                        gameData.targetOpponentId != null &&
                                        gameData.targetOpponentId.equals(playerId)) {

                                    String gameId = gameSnapshot.getKey();
                                    Log.d("FirebaseManager", "Found a game waiting for me: " + gameId);
                                    joinGame(gameId);
                                    gameFound = true;
                                    break;
                                }
                            }

                            if (!gameFound) {
                                // No paired game found, try regular matchmaking
                                findOrCreateGame();
                            }
                        } else {
                            // No paired games waiting for this player
                            findOrCreateGame();
                        }
                    } else {
                        Log.e("FirebaseManager", "Failed to find paired game: " + task.getException().getMessage());
                        findOrCreateGame();
                    }
                });
    }

    public void createGame() {
        createGame(null, isPlayerX);
    }

    // A complete rewrite of the createGame method that ensures proper player role assignment
    public void createGame(String previousWinner, boolean wasPlayerX) {
        // Reset game state
        gameEngine.newGame();

        // Create game data with proper initial values
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("status", "waiting");  // Start with waiting status
        gameData.put("currentPlayer", "X"); // X always starts
        gameData.put("currentCombo", "");
        gameData.put("createdAt", ServerValue.TIMESTAMP);
        gameData.put("previousWinner", previousWinner);
        gameData.put("playerXScore", gameEngine.getPlayerXScore());
        gameData.put("playerOScore", gameEngine.getPlayerOScore());

        // Initialize empty dice array
        Map<String, Integer> diceMap = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            diceMap.put(String.valueOf(i), 0);
        }
        gameData.put("dice", diceMap);

        // CRITICAL ROLE ASSIGNMENT LOGIC - determines who should be X/O
        boolean shouldBePlayerX = true; // Default for new games

        // Winner goes first as X, loser goes second as O
        if (previousWinner != null && !previousWinner.equals("Draw")) {
            if ((wasPlayerX && "X".equals(previousWinner)) || (!wasPlayerX && "O".equals(previousWinner))) {
                // If I won my last game, I should be X
                shouldBePlayerX = true;
            } else {
                // If I lost my last game, I should be O
                shouldBePlayerX = false;
            }
        }

        // Set player role explicitly
        Map<String, String> players = new HashMap<>();
        String myRole = shouldBePlayerX ? "X" : "O";
        players.put(playerId, myRole);
        gameData.put("players", players);

        // Record local player role
        isPlayerX = shouldBePlayerX;

        // Add player timestamp for heartbeat
        Map<String, Object> playerTimestamps = new HashMap<>();
        playerTimestamps.put(playerId, ServerValue.TIMESTAMP);
        gameData.put("playerTimestamps", playerTimestamps);

        // Initialize empty board
        Map<String, String> boardMap = new HashMap<>();
        gameData.put("board", boardMap);

        // Add creator info for synchronization
        gameData.put("gameCreatedBy", playerId);

        Log.d("FirebaseManager", "Creating new game as player " + myRole +
                " (previous winner was " + previousWinner + ")");

        // Create the game in Firebase
        DatabaseReference newGameRef = dbRef.child("games").push();
        gameId = newGameRef.getKey();

        newGameRef.setValue(gameData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("FirebaseManager", "Game created: " + gameId);

                // Get a fresh copy of the game state to ensure we have latest data
                newGameRef.get().addOnSuccessListener(dataSnapshot -> {
                    GameManager latestState = dataSnapshot.getValue(GameManager.class);
                    if (latestState != null) {
                        // Update roles and turn status before listening for changes
                        isPlayerX = "X".equals(players.get(playerId));

                        // Only set my turn to true if I'm X (first player)
                        boolean isMyTurn = isPlayerX;
                        activity.setMyTurn(isMyTurn);
                        lastIsMyTurn = isMyTurn;

                        // Now start listening for changes
                        listenToGame(gameId);

                        // Start heartbeat
                        startPlayerHeartbeat();

                        // Update UI
                        activity.runOnUiThread(() -> {
                            activity.updateBoardState();
                            activity.updateDiceDisplay();

                            String statusMessage = isPlayerX ?
                                    "Waiting for opponent to join..." :
                                    "Waiting for player X to make a move";
                            activity.showToast(statusMessage);
                        });
                    }
                });
            } else {
                Log.e("FirebaseManager", "Failed to create game: " + task.getException().getMessage());
                activity.runOnUiThread(() -> activity.showToast("Failed to create game: " + task.getException().getMessage()));
            }
        });
    }

    private void findGameByWinner(String previousWinner) {
        dbRef.child("games")
                .orderByChild("status")
                .equalTo("waiting")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DataSnapshot snapshot = task.getResult();
                        if (snapshot.exists()) {
                            boolean gameFound = false;
                            for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                                // Check if this game was created by a player who won with previousWinner
                                String winnerInGame = gameSnapshot.child("previousWinner").getValue(String.class);

                                // Look for games where previous winner is set correctly
                                if (previousWinner != null && previousWinner.equals(winnerInGame)) {
                                    String gameId = gameSnapshot.getKey();
                                    joinGame(gameId);
                                    gameFound = true;
                                    break;
                                }
                            }
                            if (!gameFound) {
                                // No game found created by winner, create a new game as player X
                                isPlayerX = true;
                                createGame(null, true);
                            }
                        } else {
                            // No waiting games at all, create a new one as player X
                            isPlayerX = true;
                            createGame(null, true);
                        }
                    } else {
                        Log.e("FirebaseManager", "Failed to find game by winner: " + task.getException().getMessage());
                        activity.runOnUiThread(() -> activity.showToast("Failed to find game: " + task.getException().getMessage()));
                        // Create a new game as fallback
                        isPlayerX = true;
                        createGame(null, true);
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
            if (!task.isSuccessful() || task.getResult() == null) return;

            DataSnapshot snapshot = task.getResult();
            long currentTime = System.currentTimeMillis();

            // First find opponents and check timestamps
            for (DataSnapshot playerSnapshot : snapshot.getChildren()) {
                String playerKey = playerSnapshot.getKey();
                if (playerKey != null && !playerKey.equals(playerId)) {
                    Long lastSeen = playerSnapshot.getValue(Long.class);
                    if (lastSeen != null) {
                        // Store these as final variables for the lambda
                        final String opponentId = playerKey;
                        final long opponentTimestamp = lastSeen;

                        // Only mark as inactive if 3 minutes have passed (180000ms)
                        if (currentTime - opponentTimestamp > 180000) {
                            // Double-check game status first to avoid false reports
                            dbRef.child("games").child(gameId).child("status").get()
                                    .addOnSuccessListener(statusSnapshot -> {
                                        String status = statusSnapshot.getValue(String.class);
                                        if ("playing".equals(status)) {
                                            handleInactiveOpponent(opponentId);
                                        }
                                    });
                        }
                        break; // Only check the first opponent found
                    }
                }
            }
        });
    }

    private void handleInactiveOpponent(String inactivePlayerId) {
        // Mark game as ended due to player inactivity
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "game_over");  // Changed from "player_left" to "game_over"
        updates.put("leftPlayer", inactivePlayerId);

        dbRef.child("games").child(gameId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseManager", "Opponent marked as inactive: " + inactivePlayerId);
                    activity.runOnUiThread(() -> activity.showToast("Opponent left the game."));
                });
    }

    private void updatePlayerRole(GameManager gameState) {
        // Determine player symbol by player ID
        String mySymbol = null;
        for (Map.Entry<String, String> entry : gameState.players.entrySet()) {
            if (entry.getKey().equals(playerId)) {
                mySymbol = entry.getValue();
                break;
            }
        }

        if (mySymbol != null) {
            // Update isPlayerX to match current role
            boolean newIsPlayerX = "X".equals(mySymbol);
            if (newIsPlayerX != isPlayerX) {
                isPlayerX = newIsPlayerX;
                Log.d("FirebaseManager", "Player role updated to: " + (isPlayerX ? "X" : "O"));
            }
        } else {
            Log.e("FirebaseManager", "Player symbol not found in game");
        }
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

                    // Parse all the basic properties from snapshot
                    gameState.currentPlayer = snapshot.child("currentPlayer").getValue(String.class);
                    gameState.status = snapshot.child("status").getValue(String.class);
                    gameState.currentCombo = snapshot.child("currentCombo").getValue(String.class);
                    gameState.gameCreatedBy = snapshot.child("gameCreatedBy").getValue(String.class);
                    gameState.previousWinner = snapshot.child("previousWinner").getValue(String.class);

                    // Handle scores
                    if (snapshot.hasChild("playerXScore")) {
                        gameState.playerXScore = snapshot.child("playerXScore").getValue(Integer.class);
                    }
                    if (snapshot.hasChild("playerOScore")) {
                        gameState.playerOScore = snapshot.child("playerOScore").getValue(Integer.class);
                    }

                    // Parse board, players, dice, etc.
                    try {
                        Map<String, String> board = new HashMap<>();
                        for (DataSnapshot child : snapshot.child("board").getChildren()) {
                            String key = child.getKey();
                            String value = child.getValue(String.class);
                            if (key != null && value != null) {
                                board.put(key, value);
                            }
                        }
                        gameState.board = board;
                    } catch (Exception e) {
                        Log.e("FirebaseManager", "Error parsing board data", e);
                        gameState.board = new HashMap<>();
                    }

                    try {
                        Map<String, String> players = new HashMap<>();
                        for (DataSnapshot child : snapshot.child("players").getChildren()) {
                            String key = child.getKey();
                            String value = child.getValue(String.class);
                            if (key != null && value != null) {
                                players.put(key, value);
                            }
                        }
                        gameState.players = players;
                    } catch (Exception e) {
                        Log.e("FirebaseManager", "Error parsing players data", e);
                        gameState.players = new HashMap<>();
                    }

                    // Parse dice data
                    try {
                        gameState.dice = snapshot.child("dice").getValue();
                    } catch (Exception e) {
                        // Set default dice values
                        Map<String, Integer> defaultDice = new HashMap<>();
                        for (int i = 0; i < 5; i++) {
                            defaultDice.put(String.valueOf(i), 0);
                        }
                        gameState.dice = defaultDice;
                    }

                    // Handle player left state
                    if ("player_left".equals(gameState.status)) {
                        String leftPlayer = snapshot.child("leftPlayer").getValue(String.class);
                        if (leftPlayer != null && !leftPlayer.equals(playerId)) {
                            activity.runOnUiThread(() -> {
                                activity.showToast("Opponent left the game.");
                            });
                        }
                    }

                    // Update UI on main thread
                    activity.runOnUiThread(() -> {
                        // First update player role
                        updatePlayerRole(gameState);

                        // CRITICAL FIX: Don't update turn state if game is over or player left
                        if ("playing".equals(gameState.status)) {
                            updateTurnStatus(gameState);
                        }

                        // Update game state
                        updateLocalGame(gameState);
                    });
                } catch (Exception e) {
                    Log.e("FirebaseManager", "Error parsing game data", e);
                    e.printStackTrace();
                    activity.runOnUiThread(() -> activity.showToast("Error parsing game data: " + e.getMessage()));
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
            remoteState.board = new HashMap<>();
            Log.w("FirebaseManager", "Remote board was null, initialized to empty map");
        }

        // Update scores if they exist in remote state
        Log.d("FirebaseManager", "Updating local scores - X: " + remoteState.playerXScore + ", O: " + remoteState.playerOScore);
        gameEngine.setPlayerXScore(remoteState.playerXScore);
        gameEngine.setPlayerOScore(remoteState.playerOScore);

        gameEngine.syncWithRemote(remoteState);
        activity.updateBoardState();

        if ("game_over".equals(remoteState.status)) {
            String winner = gameEngine.getWinner();
            String winnerMessage = "Game Over: ";

            if (winner != null && !"Draw".equals(winner)) {
                winnerMessage += "Player " + winner + " wins!";

                // Update previousWinner field in Firebase
                if (gameId != null) {
                    dbRef.child("games").child(gameId).child("previousWinner").setValue(winner);
                }
            } else {
                winnerMessage += "It's a Draw!";
            }
            activity.showToast(winnerMessage);
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

        // Determine if it's my turn
        boolean isMyTurn = mySymbol.equals(currentPlayer);

        // Only update UI if turn state changed
        if (isMyTurn != lastIsMyTurn) {
            Log.d("FirebaseManager", "Turn status changed to: " + (isMyTurn ? "MY TURN" : "OPPONENT TURN"));
            lastIsMyTurn = isMyTurn;

            // Update UI with proper turn state
            activity.setMyTurn(isMyTurn);

            // Show toast notification only if it's now my turn
            if (isMyTurn) {
                activity.showToast("Your turn!");
            }
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

    public void updateScores(int playerXScore, int playerOScore) {
        if (gameId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("playerXScore", playerXScore);
        updates.put("playerOScore", playerOScore);

        // Update scores in Firebase
        dbRef.child("games").child(gameId).updateChildren(updates)
                .addOnSuccessListener(aVoid ->
                        Log.d("FirebaseManager", "Scores updated successfully"))
                .addOnFailureListener(e -> {
                    Log.e("FirebaseManager", "Failed to update scores: " + e.getMessage());
                    activity.runOnUiThread(() ->
                            activity.showToast("Failed to update scores: " + e.getMessage()));
                });
    }

    public void resetAndCreateNewGame() {
        if (gameId == null) {
            createGame(null, isPlayerX);
            return;
        }

        // Store the current game state before ending
        String previousWinner = gameEngine.getWinner();
        boolean wasPlayerX = isPlayerX;

        // Store opponent information to try to rejoin the same opponent later
        String opponentId = null;

        // Get the reference to the current game to find the opponent
        dbRef.child("games").child(gameId).get().addOnSuccessListener(snapshot -> {
            GameManager currentGame = snapshot.getValue(GameManager.class);
            String foundOpponentId = null;

            // Find the opponent player ID from the current game's players
            if (currentGame != null && currentGame.players != null) {
                for (Map.Entry<String, String> playerEntry : currentGame.players.entrySet()) {
                    if (!playerEntry.getKey().equals(playerId)) {
                        foundOpponentId = playerEntry.getKey();
                        break;
                    }
                }
            }

            // Now proceed with game reset using the found opponent ID
            continueGameReset(previousWinner, wasPlayerX, foundOpponentId);
        }).addOnFailureListener(e -> {
            // If we fail to get opponent data, just proceed without it
            Log.e("FirebaseManager", "Failed to get current game data: " + e.getMessage());
            continueGameReset(previousWinner, wasPlayerX, null);
        });
    }

    // Helper method to continue the game reset process once we have (or failed to get) opponent info
    private void continueGameReset(String previousWinner, boolean wasPlayerX, String opponentId) {
        // Mark the current game as complete
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "game_over");
        updates.put("previousWinner", previousWinner);
        updates.put("pairPreserve", true); // Add this flag to indicate we want to preserve the pairing
        updates.put("lastGameId", gameId); // Store current game ID for reference

        dbRef.child("games").child(gameId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseManager", "Game ended with winner: " + previousWinner);

                    // Cleanup of old game
                    String oldGameId = gameId;
                    gameId = null;

                    if (gameRef != null && gameListener != null) {
                        gameRef.removeEventListener(gameListener);
                        gameListener = null;
                    }

                    // Reset local game state
                    gameEngine.newGame();
                    activity.runOnUiThread(() -> {
                        activity.updateBoardState();
                        activity.updateDiceDisplay();
                        activity.showToast("Starting new game...");
                    });

                    // Wait a brief moment to ensure game is fully closed on server
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    // First, check if our opponent has already created a game waiting for us
                    if (opponentId != null) {
                        // Look for a waiting game created by our opponent
                        dbRef.child("games")
                                .orderByChild("status")
                                .equalTo("waiting")
                                .get()
                                .addOnSuccessListener(snapshot -> {
                                    boolean foundOpponentGame = false;

                                    for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                                        GameManager gameData = gameSnapshot.getValue(GameManager.class);
                                        if (gameData != null &&
                                                gameData.gameCreatedBy != null &&
                                                gameData.gameCreatedBy.equals(opponentId) &&
                                                gameData.players != null &&
                                                gameData.players.size() == 1 &&
                                                gameData.pairPreserve) {

                                            // Found a waiting game from our opponent! Join it
                                            String newGameId = gameSnapshot.getKey();
                                            Log.d("FirebaseManager", "Found opponent's waiting game: " + newGameId);
                                            joinGame(newGameId);
                                            foundOpponentGame = true;
                                            break;
                                        }
                                    }

                                    if (!foundOpponentGame) {
                                        // No waiting game from opponent, create our own
                                        createPairedGame(previousWinner, wasPlayerX, opponentId, oldGameId);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // Failed to find game, create a new one anyway
                                    createPairedGame(previousWinner, wasPlayerX, opponentId, oldGameId);
                                });
                    } else {
                        // No opponent info, just create a regular new game
                        createGame(previousWinner, wasPlayerX);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseManager", "Failed to end game: " + e.getMessage());
                    // Create new game anyway
                    gameEngine.newGame();
                    createGame(previousWinner, wasPlayerX);
                });
    }

    // Method to create a game that preserves pairing with a specific opponent
    private void createPairedGame(String previousWinner, boolean wasPlayerX, String targetOpponentId, String previousGameId) {
        // Reset game state
        gameEngine.newGame();

        // Create game data with proper initial values
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("status", "waiting");  // Start with waiting status
        gameData.put("currentPlayer", "X"); // X always starts
        gameData.put("currentCombo", "");
        gameData.put("createdAt", ServerValue.TIMESTAMP);
        gameData.put("previousWinner", previousWinner);

        // Preserve the previous scores rather than resetting them
        gameData.put("playerXScore", gameEngine.getPlayerXScore());
        gameData.put("playerOScore", gameEngine.getPlayerOScore());

        // Add pairing information
        gameData.put("pairPreserve", true);
        gameData.put("targetOpponentId", targetOpponentId);
        gameData.put("previousGameId", previousGameId);

        // Initialize empty dice array
        Map<String, Integer> diceMap = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            diceMap.put(String.valueOf(i), 0);
        }
        gameData.put("dice", diceMap);

        // CRITICAL ROLE ASSIGNMENT LOGIC - determines who should be X/O
        boolean shouldBePlayerX = true; // Default for new games

        // Winner goes first as X, loser goes second as O
        if (previousWinner != null && !previousWinner.equals("Draw")) {
            if ((wasPlayerX && "X".equals(previousWinner)) || (!wasPlayerX && "O".equals(previousWinner))) {
                // If I won my last game, I should be X
                shouldBePlayerX = true;
            } else {
                // If I lost my last game, I should be O
                shouldBePlayerX = false;
            }
        }

        // Set player role explicitly
        Map<String, String> players = new HashMap<>();
        String myRole = shouldBePlayerX ? "X" : "O";
        players.put(playerId, myRole);
        gameData.put("players", players);

        // Record local player role
        isPlayerX = shouldBePlayerX;

        // Add player timestamp for heartbeat
        Map<String, Object> playerTimestamps = new HashMap<>();
        playerTimestamps.put(playerId, ServerValue.TIMESTAMP);
        gameData.put("playerTimestamps", playerTimestamps);

        // Initialize empty board
        Map<String, String> boardMap = new HashMap<>();
        gameData.put("board", boardMap);

        // Add creator info for synchronization
        gameData.put("gameCreatedBy", playerId);

        Log.d("FirebaseManager", "Creating paired game as player " + myRole +
                " for opponent " + targetOpponentId +
                " (previous winner was " + previousWinner + ")");

        // Create the game in Firebase
        DatabaseReference newGameRef = dbRef.child("games").push();
        gameId = newGameRef.getKey();

        newGameRef.setValue(gameData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("FirebaseManager", "Paired game created: " + gameId);

                // Get a fresh copy of the game state to ensure we have latest data
                newGameRef.get().addOnSuccessListener(dataSnapshot -> {
                    GameManager latestState = dataSnapshot.getValue(GameManager.class);
                    if (latestState != null) {
                        // Update roles and turn status before listening for changes
                        isPlayerX = "X".equals(players.get(playerId));

                        // Only set my turn to true if I'm X (first player)
                        boolean isMyTurn = isPlayerX;
                        activity.setMyTurn(isMyTurn);
                        lastIsMyTurn = isMyTurn;

                        // Now start listening for changes
                        listenToGame(gameId);

                        // Start heartbeat
                        startPlayerHeartbeat();

                        // Update UI
                        activity.runOnUiThread(() -> {
                            activity.updateBoardState();
                            activity.updateDiceDisplay();

                            String statusMessage = isPlayerX ?
                                    "Waiting for your opponent to join..." :
                                    "Waiting for player X to make a move";
                            activity.showToast(statusMessage);
                        });
                    }
                });
            } else {
                Log.e("FirebaseManager", "Failed to create paired game: " + task.getException().getMessage());
                activity.runOnUiThread(() -> {
                    activity.showToast("Failed to create game: " + task.getException().getMessage());
                });
            }
        });
    }

    public void endGame() {
        if (gameId != null) {
            String winner = gameEngine.getWinner();
            Log.d("FirebaseManager", "Ending game with scores - X: " + gameEngine.getPlayerXScore() + ", O: " + gameEngine.getPlayerOScore());

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "game_over");
            updates.put("playerXScore", gameEngine.getPlayerXScore());
            updates.put("playerOScore", gameEngine.getPlayerOScore());

            if (winner != null && !"Draw".equals(winner)) {
                updates.put("previousWinner", winner);
            }

            dbRef.child("games").child(gameId).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("FirebaseManager", "Game ended and scores updated successfully");
                        // Force an immediate update to scores in case updateChildren callback happens before the listener
                        updateScores(gameEngine.getPlayerXScore(), gameEngine.getPlayerOScore());
                    })
                    .addOnFailureListener(e ->
                            Log.e("FirebaseManager", "Failed to end game: " + e.getMessage()));
        }
    }

    public void leaveGame() {
        if (gameId == null) return;

        isLeavingGame = true;

        // Update game status to indicate player left, but use "game_over" instead of "player_left"
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "game_over");  // Changed from "player_left" to "game_over"
        updates.put("leftPlayer", playerId); // Still keep track of who left

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

    public void cleanupPlayerGames() {
        if (playerId == null) return;

        // First find games involving this player
        dbRef.child("games")
                .orderByChild("status")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> gamesToCleanup = new ArrayList<>();

                        for (DataSnapshot gameSnapshot : task.getResult().getChildren()) {
                            GameManager game = gameSnapshot.getValue(GameManager.class);
                            if (game != null && game.players != null) {
                                boolean playerInvolved = false;

                                // Check if this player is in the game
                                for (Map.Entry<String, String> entry : game.players.entrySet()) {
                                    if (entry.getKey().equals(playerId)) {
                                        playerInvolved = true;
                                        break;
                                    }
                                }

                                // Also check if this game is waiting for this player specifically
                                if (game.targetOpponentId != null && game.targetOpponentId.equals(playerId)) {
                                    playerInvolved = true;
                                }

                                // If player is involved, check if game needs cleanup
                                if (playerInvolved) {
                                    String gameId = gameSnapshot.getKey();

                                    // Check problematic game states
                                    if ("player_left".equals(game.status)) {
                                        // Games marked as "player_left" should be cleaned up
                                        gamesToCleanup.add(gameId);
                                    } else if ("waiting".equals(game.status) &&
                                            game.createdAt instanceof Long &&
                                            (System.currentTimeMillis() - (Long)game.createdAt > 5 * 60 * 1000)) {
                                        // Waiting games older than 5 minutes should be cleaned up
                                        gamesToCleanup.add(gameId);
                                    }
                                }
                            }
                        }

                        // Now clean up each identified game
                        if (!gamesToCleanup.isEmpty()) {
                            Log.d("FirebaseManager", "Found " + gamesToCleanup.size() + " games to clean up");

                            for (String gameId : gamesToCleanup) {
                                // Mark the game as completed instead of "player_left"
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("status", "game_over");

                                dbRef.child("games").child(gameId).updateChildren(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d("FirebaseManager", "Successfully cleaned up game: " + gameId);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("FirebaseManager", "Failed to clean up game: " + e.getMessage());
                                        });
                            }

                            activity.runOnUiThread(() ->
                                    activity.showToast("Cleaned up " + gamesToCleanup.size() + " old games"));
                        } else {
                            Log.d("FirebaseManager", "No games need cleanup");
                        }
                    } else {
                        Log.e("FirebaseManager", "Failed to get games for cleanup: " +
                                (task.getException() != null ? task.getException().getMessage() : "unknown error"));
                    }
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