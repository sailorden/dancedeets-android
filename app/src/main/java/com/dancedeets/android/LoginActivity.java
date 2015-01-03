package com.dancedeets.android;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.location.Address;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LoginActivity extends FacebookActivity {

    private static final String LOG_TAG = "LoginActivity";

    public void trackLoginNav(String page) {
        try {
            JSONObject props = new JSONObject();
            props.put("Link", page);
            ((DanceDeetsApp) getApplication()).getMixPanel().track("Login Nav", props);
        } catch (JSONException e) {
        }
    }

    public void clickedExplainWhyLogin(View view) {
        trackLoginNav("Explain Why Login");
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("ResourceID", R.layout.login_explain_why);
        intent.setAction(Intent.ACTION_DEFAULT);
        startActivity(intent);
    }

    public void clickedUseWithoutFacebookLogin(View view) {
        trackLoginNav("Use Without Facebook");
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("ResourceID", R.layout.login_use_without_facebook_login);
        intent.setAction(Intent.ACTION_DEFAULT);
        startActivity(intent);
    }

    public void clickedUseWebsite(View view) {
        trackLoginNav("Use Website");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.dancedeets.com/"));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private static class SendAuthRequest implements FetchLocation.AddressListener {
        private static final String LOG_TAG = "SendAuthRequest";

        private final MixpanelAPI mMixPanel;
        private final Session mSession;
        private final FetchLocation mFetchLocation;

        SendAuthRequest(MixpanelAPI mixPanel, Session session, FetchLocation fetchLocation) {
            Log.i(LOG_TAG, "Received session " + session + ", with " + fetchLocation);
            mSession = session;
            mFetchLocation = fetchLocation;
            mMixPanel = mixPanel;
        }

        @Override
        public void onAddressFound(Location location, Address address) {
            Log.i(LOG_TAG, "onAddressFound with location: " + location + ", address: " + address);

            String addressString = null;
            if (address != null) {
                addressString = address.getLocality() + ", " + address.getAdminArea() + ", " + address.getCountryCode();

                // Only do this if we have an address, so future events get tagged with the user's location
                try {
                    JSONObject props = new JSONObject();
                    props.put("City", addressString);
                    props.put("Country", address.getCountryCode());
                    mMixPanel.registerSuperProperties(props);
                } catch (JSONException e) {
                }
            } else {
                Log.e(LOG_TAG, "Failed to get address from server, sending update with empty location.");
            }
            DanceDeetsApi.sendAuth(mSession, addressString);

            mFetchLocation.onStop();
        }
    }

    // Static context so we don't retain a reference to the possibly-destroyed Activity
    static class MeCompleted implements Request.GraphUserCallback {

        private final Session mSession;
        private final MixpanelAPI mMixPanel;

        MeCompleted(MixpanelAPI mixPanel, Session session) {
            mMixPanel = mixPanel;
            mSession = session;
        }
        @Override
        public void onCompleted(GraphUser user, Response response) {
            if (mSession == Session.getActiveSession()) {
                if (user != null) {
                    // When we log-in (auto or manual), dentify as this uid,
                    // so all future events (across website and ios/android)
                    // can be correlated with each other.
                    mMixPanel.identify(user.getId());
                }
            }
        }
    }
    protected void onSessionStateChange(Session session, SessionState state, Exception exception) {
        // Don't call the super, since we don't want it sending us back to the LoginActivity when logged out
        // super.onSessionStateChange(session, state, exception);
        if (state.isOpened()) {
            Log.i(LOG_TAG, "Activity " + this + " is logged in, with state: " + state);
            trackLoginNav("Logged In");

            MixpanelAPI mixPanel = ((DanceDeetsApp) getApplication()).getMixPanel();
            Request.executeBatchAsync(Request.newMeRequest(session, new MeCompleted(mixPanel, session)));

            FetchLocation fetchLocation = new FetchLocation();
            fetchLocation.onStart(this, new SendAuthRequest(mixPanel, session, fetchLocation));

            Intent intent = new Intent(this, EventListActivity.class);
            intent.setAction(Intent.ACTION_DEFAULT);
            // We can't use the noHistory option on this activity as we need state retained:
            // Facebook login navigate to a sub-activity expecting a response back to this one.
            // So any navigation away must manually ensure there is no history stack retained.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if (state.isClosed()) {
            Log.i(LOG_TAG, "Activity " + this + " is logged out, with state: " + state);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        VolleySingleton.createInstance(getApplicationContext());
        super.onCreate(savedInstanceState);

        // Set (DEBUG) title
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            //The .debug specified in gradle
            if (pInfo.packageName.equals("com.dancedeets.android.debug")) {
                setTitle(getTitle() + " (DEBUG)");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Print out the KeyHashes used.
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d(LOG_TAG, "KeyHash: " + Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {

        } catch (NoSuchAlgorithmException e) {

        }

        Intent intent = getIntent();
        int layoutId = R.layout.login;
        if (intent != null) {
            if (intent.hasExtra("ResourceID") && getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            }
            layoutId = intent.getIntExtra("ResourceID", R.layout.login);
        }
        setContentView(layoutId);

        TextView link1 = (TextView)findViewById(R.id.login_use_without_fblogin);
        if (link1 != null) {
            link1.setTextColor(link1.getLinkTextColors());
        }
        TextView link2 = (TextView)findViewById(R.id.login_explain_why_login);
        if (link2 != null) {
            link2.setTextColor(link2.getLinkTextColors());
        }

        LoginButton authButton = (LoginButton)findViewById(R.id.authButton);
        authButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackLoginNav("Login Pressed");
            }
        });
        // We should ask for "rsvp_event" later, when needed to actually rsvp for the user? And implement that on the website, too?
        authButton.setReadPermissions("email", "public_profile", "user_events", "user_friends");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.login_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_feedback:
                SendFeedback.sendFeedback(this, null);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
