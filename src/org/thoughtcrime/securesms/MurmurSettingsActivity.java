package org.thoughtcrime.securesms;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.murmur.ui.MurmurProfileFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

/**
 * Activity for submitting logcat logs to a pastebin service.
 */
public class MurmurSettingsActivity extends PassphraseRequiredActionBarActivity {

    private static final String TAG = LogSubmitActivity.class.getSimpleName();
    private DynamicTheme dynamicTheme = new DynamicTheme();

    private MasterSecret masterSecret;
    private String LOG_TAG = "MurmurSettingsActivity";

    @Override
    protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
        dynamicTheme.onCreate(this);
        Log.d(LOG_TAG, "super.onCreate");

        this.masterSecret = masterSecret;
        final MasterSecret masterSecretCopy = masterSecret;

        setContentView(R.layout.murmur_settings_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        MurmurProfileFragment fragment = new MurmurProfileFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                Context context      = MurmurSettingsActivity.this;

                TextSecureDirectory tsd = TextSecureDirectory.getInstance(context);
                List<String> activeNumbers = tsd.getActiveNumbers();
                Recipients recipients = RecipientFactory.getRecipientsFromStrings(context, activeNumbers, false);

                for (Recipient r : recipients) {
                    Optional<IdentityDatabase.IdentityRecord> ir =
                            DatabaseFactory.getIdentityDatabase(context)
                            .getIdentity(r.getRecipientId());

                    if(ir.isPresent()) {
                        ECPublicKey k = ir.get().getIdentityKey().getPublicKey();
                        Log.d(LOG_TAG, r.getNumber() + " has key: " + k.serialize());
                    }
                }
                return null;
            }
        }.execute();

    }

    @Override
    protected void onResume() {
        dynamicTheme.onResume(this);
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return false;
    }

    @Override
    public void startActivity(Intent intent) {
        try {
            super.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, e);
            Toast.makeText(this, R.string.log_submit_activity__no_browser_installed, Toast.LENGTH_LONG).show();
        }
    }
}
