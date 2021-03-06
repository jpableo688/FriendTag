package com.github.jovenpableo.friendtag.firebase;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.github.jovenpableo.friendtag.entity.User;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class UserManager {
    private static final UserManager ourInstance = new UserManager();

    private final String TAG = "ucsc-tag";
    private final String TABLE_NAME = "users";

    private FirebaseFirestore db;
    private FirebaseUser currentFirebaseUser;

    public Map<String, User> users;
    public ArrayList<User> friends;

    private FusedLocationProviderClient mFusedLocationClient;

    private UserManager() {
        db = FirebaseFirestore.getInstance();

        currentFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        this.loadUsers();
    }

    public ArrayList<User> getFriends() {
        friends = new ArrayList<>();

        ArrayList<String> friendUids = getCurrentUser().getFriends();
        for (String friendUid : friendUids) {
            User friend = users.get(friendUid);
            friends.add(friend);
        }

        return friends;
    }

    public static UserManager getInstance() {
        return ourInstance;
    }

    @SuppressLint("MissingPermission")
    public Location getLocation(Activity context) {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        Log.i(TAG, "getLocation called");

        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(context, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location loc) {
                        Log.i(TAG, "Location retrieved");
                        if (loc != null) {
                            getCurrentUser().setLocation(loc);
                            getCurrentUser().write(db);
                            Log.i(TAG, "Writing location (" + loc.getLatitude() + ", " + loc.getLongitude() + ")");
                        } else {
                            Log.e(TAG, "Could not retrieve location");
                        }
                    }
                });

        Log.i(TAG, "Got location i think");

        return getCurrentUser().getLocation();
    }

    public User getUser(String uid) {
        return users.get(uid);
    }

    private void loadUsers() {
        users = new HashMap<String, User>();

        db.collection(TABLE_NAME).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        User user = new User(document.getData());
                        users.put(user.getUid(), user);
                    }
                    Log.i(TAG, "Done downloading all users from firestore");
                } else {
                    Log.e(TAG, "Error getting documents: ", task.getException());
                }
            }
        });
    }

    public void getAll(final Callable<Void> methodParam) {
        users = new HashMap<String, User>();

        db.collection(TABLE_NAME).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        User user = new User(document.getData());
                        users.put(user.getUid(), user);
                    }

                    try {
                        methodParam.call();
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                } else {
                    Log.e(TAG, "Error getting documents: ", task.getException());
                }
            }
        });
    }

    public ArrayList<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public User getCurrentUser() {
        String currentUid = currentFirebaseUser.getUid();
        return users.get(currentUid);
    }

    public boolean tag(User user) {
        if (getCurrentUser().equals(user)) {
            Log.i(TAG, "Cannot tag ourselves");
            return false;
        }

        // NOTE: Check if tag time is valid
        Date lastTagTime = getCurrentUser().getTagTime(user);
        if (lastTagTime != null) {
            Date currentTime = Calendar.getInstance().getTime();

            long difference = currentTime.getTime() - lastTagTime.getTime();
            long diffMinutes = difference / (60 * 1000);
            Log.i(TAG, "Difference in time is at " + diffMinutes);

            if (diffMinutes < 15) {
                Log.i(TAG, "Tag is on cooldown for this user");

//                return false;
            }
        }

        if (getDistance(user) > 1.0) {
            Log.i(TAG, "They're too far away");

            return false;
        }

        getCurrentUser().tag(user);
        getCurrentUser().write(db);

        return true;
    }

    public double getDistance(User user) {
        Location here = getCurrentUser().getLocation();
        Location there = user.getLocation();

        double distanceMeters = here.distanceTo(there);
        double distanceMiles = distanceMeters / 1609.344; // NOTE: Courtesy of Google
        distanceMiles = Math.round(distanceMiles * 100) / 100;

        return distanceMiles;
    }

    public void addFriend(User user) {
        User current = getCurrentUser();
        current.addFriend(user);
        current.write(db);
    }

    public void write(User user) {
        db.collection(TABLE_NAME)
                .document(user.getUid())
                .update(user.toMap())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });
    }

    public void create(User user) {
        db.collection(TABLE_NAME)
                .document(user.getUid())
                .set(user.toMap())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });
    }


}
