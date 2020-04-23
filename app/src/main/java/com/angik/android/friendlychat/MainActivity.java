package com.angik.android.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int RC_PHOTO_PICKER = 2;

    public static final int RC_SIGN_IN = 1;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private FirebaseDatabase mFirebaseDatabase;//Entry point
    private FirebaseStorage mFirebaseStorage;//Storage entry point
    private DatabaseReference mMessagesDatabaseReference;//Points to a specific part of the database
    private StorageReference mChatPhotoStorageReference;

    private FirebaseAuth mFirebaseAuth;//FirebaseAuth object that handles authentication process
    private FirebaseAuth.AuthStateListener mAuthStateListener;//Authentication state listener, singed in or out
    private ChildEventListener mChildEventListener;//Child event listener for the database ref, if any of the child gets changed

    private String mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();//Getting firebase auth objects instance;

        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");//Ref to specific node
        mChatPhotoStorageReference = mFirebaseStorage.getReference().child("chat_photos");

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);//This is custom adapter for list view which takes list of friendlyMessages objects
        mMessageListView.setAdapter(mMessageAdapter);//Initially setting the adapter, but there is nothing

        // Initialize progress bar
        //mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //Listening to the edit text
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //After clicking we are making a friendly message object by passing 3 arguments
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);//Pushing to database
                // Clear input box
                mMessageEditText.setText("");
            }
        });

        //Listening to the auth state to handle the work during signed in and signed out state
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //We will get the current user info by firebaseAuth

                FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {
                    //If the user is logged in
                    onSingedInInitialize(user.getPhoneNumber());
                } else {
                    onSingeOutCleanup();
                    //If the user is not logged in, we want to show the FirebaseUI to sign in the user
                    // Choose authentication providers
                    List<AuthUI.IdpConfig> providers = Collections.singletonList(
                            new AuthUI.IdpConfig.PhoneBuilder().build());

                    // Create and launch sign-in intent
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Singed in!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sing in cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            Uri selectedPhotoUri = data.getData();
            //This is new storage ref where we are uploading the image into the child of our previous ref
            StorageReference photoRef = mChatPhotoStorageReference.child(selectedPhotoUri.getLastPathSegment());

            photoRef.putFile(selectedPhotoUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    //Getting the download url for the uploaded image
                    Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                    while (!uriTask.isSuccessful()) ;//without this line app will crash
                    //As in image case, there is no text, the first parameter is null
                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, uriTask.getResult().toString());
                    mMessagesDatabaseReference.push().setValue(friendlyMessage);
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //If the current activity is on pause
    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            //We need to remove the auth state listener on pause as the activity is not visible
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        //This code handles when the user doesn't log out but the activity gets destroyed or screen gets rotated
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    //If the current activity is on, after pausing
    @Override
    protected void onResume() {
        super.onResume();
        //Activity is opened again
        //We need to add the auth state listener to te FirebaseAuth object that we made to indicate the state of the current user
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);//mAuthStateListener we made for our code
    }

    private void onSingedInInitialize(String userName) {
        //Here we are getting the user name from the current user who is logged in
        mUsername = userName;

        //new lines
        new LoadData(MainActivity.this).execute();
        //attachDatabaseReadListener();//After logging in, we are attaching the read listener to show the chat
    }

    private void onSingeOutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    private void attachDatabaseReadListener() {
        //If event listener is null means if it already been detached
        if (mChildEventListener == null) {
            //And now we are calling childEventListener as the user logged in allowed to see the chat
            //That's why we moved this block of code
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    FriendlyMessage message = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(message);
                    mMessageAdapter.notifyDataSetChanged();
                    mProgressBar.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            //This also means if the listener is full, user is logged in. Because a listener will be full if the user is logged in
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;//After removing the listener from the database ref we are setting the listener to null as we don't have any use of it
        }
    }

    public static class LoadData extends AsyncTask<Void, Void, Void> {

        private WeakReference<MainActivity> activityWeakReference;

        LoadData(MainActivity activity) {
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();


            MainActivity activity = activityWeakReference.get();

            if (activity == null || activity.isFinishing()) {
                return;
            }

            activity.mProgressBar.setIndeterminate(true);
            activity.mProgressBar.setVisibility(View.VISIBLE);

            Log.d(TAG, "onPreExecute:");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected Void doInBackground(Void... voids) {

            MainActivity activity = activityWeakReference.get();

            if (activity == null || activity.isFinishing()) {
                return null;
            }

            publishProgress();

            Log.d(TAG, "doInBackground:");

            activity.attachDatabaseReadListener();

            return null;
        }
    }
}
