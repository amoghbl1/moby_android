/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.denovogroup.murmur.backend.AppConstants;
import org.denovogroup.murmur.backend.MurmurService;
import org.denovogroup.murmur.backend.SecurityManager;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener
{
  private static final String TAG = ConversationListActivity.class.getSimpleName();

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ConversationListFragment fragment;
  private ContentObserver observer;
  private MasterSecret masterSecret;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
    getSupportActionBar().setTitle(R.string.app_name);
    fragment = initFragment(android.R.id.content, new ConversationListFragment(), masterSecret, dynamicLanguage.getCurrentLocale());

    initializeContactUpdatesReceiver();
    initialiseMurmurService();

    RatingManager.showRatingDialogIfNecessary(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onDestroy() {
    if (observer != null) getContentResolver().unregisterContentObserver(observer);
    super.onDestroy();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(this));

    inflater.inflate(R.menu.conversation_list, menu);
    MenuItem menuItem = menu.findItem(R.id.menu_search);
    initializeSearch(menuItem);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void initializeSearch(MenuItem searchViewItem) {
    SearchView searchView = (SearchView)MenuItemCompat.getActionView(searchViewItem);
    searchView.setQueryHint(getString(R.string.ConversationListActivity_search));
    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        if (fragment != null) {
          fragment.setQueryFilter(query);
          return true;
        }

        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return onQueryTextSubmit(newText);
      }
    });

    MenuItemCompat.setOnActionExpandListener(searchViewItem, new MenuItemCompat.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem menuItem) {
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem menuItem) {
        if (fragment != null) {
          fragment.resetQueryFilter();
        }

        return true;
      }
    });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_murmur_toggle:     toggleMurmurState();     return true;
    case R.id.menu_new_group:         createGroup();           return true;
    case R.id.menu_settings:          handleDisplaySettings(); return true;
    case R.id.menu_clear_passphrase:  handleClearPassphrase(); return true;
    case R.id.menu_mark_all_read:     handleMarkAllRead();     return true;
    case R.id.menu_import_export:     handleImportExport();    return true;
    case R.id.menu_invite:            handleInvite();          return true;
    case R.id.menu_help:              handleHelp();            return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType, long lastSeen) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);
    intent.putExtra(ConversationActivity.TIMING_EXTRA, System.currentTimeMillis());
    intent.putExtra(ConversationActivity.LAST_SEEN_EXTRA, lastSeen);

    startActivity(intent);
    overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out);
  }

  @Override
  public void onSwitchToArchive() {
    Intent intent = new Intent(this, ConversationListArchiveActivity.class);
    startActivity(intent);
  }

  private void createGroup() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    startActivity(intent);
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    startActivity(preferencesIntent);
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }

  private void handleImportExport() {
    startActivity(new Intent(this, ImportExportActivity.class));
  }

  private void handleMarkAllRead() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getThreadDatabase(ConversationListActivity.this).setAllThreadsRead();
        MessageNotifier.updateNotification(ConversationListActivity.this, masterSecret);
        return null;
      }
    }.execute();
  }

  private void handleInvite() {
    startActivity(new Intent(this, InviteActivity.class));
  }

  private void handleHelp() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://support.whispersystems.org")));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.ConversationListActivity_there_is_no_browser_installed_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  private void toggleMurmurState() {
    Boolean flag = false;
    SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.PREF_FILE, MODE_PRIVATE);
    if(!sharedPreferences.getBoolean(AppConstants.IS_APP_ENABLED, false)){
      flag = true;
    }
    Log.d(TAG, "Setting state to: " + flag);
    sharedPreferences.edit().putBoolean(AppConstants.IS_APP_ENABLED, flag).commit();
  }

  private void initialiseMurmurService() {
    //start Murmur service if necessary
    SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.PREF_FILE, MODE_PRIVATE);
    if (sharedPreferences.getBoolean(AppConstants.IS_APP_ENABLED, true)) {
      if(Build.VERSION.SDK_INT >= 23 && SecurityManager.getStoredMAC(this).length() == 0){
        //need to request MAC from user first
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.onboarding4_title);
        dialog.setMessage(R.string.onboarding4_message);
        final ViewGroup contentView = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.request_mac_dialog, null, false);
        dialog.setView(contentView);
        dialog.setCancelable(false);
        dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            SecurityManager.setStoredMAC(ConversationListActivity.this, ((EditText) contentView.findViewById(R.id.mac_input)).getText().toString());
            dialog.dismiss();

            Intent startServiceIntent = new Intent(ConversationListActivity.this, MurmurService.class);
            startService(startServiceIntent);
          }
        });
        dialog.setNeutralButton(R.string.settings_bt_device_settings, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
          }
        });
        final AlertDialog alertdialog = dialog.create();
        alertdialog.show();

        alertdialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            final Intent i = new Intent();
            i.setAction(Intent.ACTION_MAIN);
            i.setComponent(new ComponentName("com.android.settings", "com.android.settings.deviceinfo.Status"));
            startActivity(i);
          }
        });

        alertdialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);

        TextWatcher watcher = new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {

          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            alertdialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setEnabled(BluetoothAdapter.checkBluetoothAddress(s.toString().toUpperCase()));
          }

          @Override
          public void afterTextChanged(Editable s) {

          }
        };

        ((EditText) contentView.findViewById(R.id.mac_input)).addTextChangedListener(watcher);
        contentView.findViewById(R.id.why_mac).setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            ((TextView) v).setText(R.string.onboarding4_message_small);
            v.setOnClickListener(null);
          }
        });

      } else {
        Intent startServiceIntent = new Intent(this, MurmurService.class);
        startService(startServiceIntent);
      }
    } else {
      // TODO: amoghbl1, once we start to handle offline and online modes for our Signal.
      //Toast.makeText(this, R.string.offline_mode_toast, Toast.LENGTH_LONG).show();
    }
  }

  private void initializeContactUpdatesReceiver() {
    observer = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        Log.w(TAG, "Detected android contact data changed, refreshing cache");
        RecipientFactory.clearCache(ConversationListActivity.this);
        ConversationListActivity.this.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            fragment.getListAdapter().notifyDataSetChanged();
          }
        });
      }
    };

    getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                                                 true, observer);
  }
}
