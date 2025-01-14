/*
 * Copyright (C) 2008 Esmertec AG. Copyright (C) 2008 The Android Open Source
 * Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package info.guardianproject.otr.app.im.app;

import static android.provider.Contacts.ContactMethods.CONTENT_EMAIL_URI;
import info.guardianproject.otr.OtrAndroidKeyManagerImpl;
import info.guardianproject.otr.app.im.IChatSession;
import info.guardianproject.otr.app.im.IContactList;
import info.guardianproject.otr.app.im.IContactListManager;
import info.guardianproject.otr.app.im.IImConnection;
import info.guardianproject.otr.app.im.R;
import info.guardianproject.otr.app.im.engine.ImErrorInfo;
import info.guardianproject.otr.app.im.plugin.BrandingResourceIDs;
import info.guardianproject.otr.app.im.provider.Imps;
import info.guardianproject.otr.app.im.ui.SecureCameraActivity;

import java.io.File;
import java.util.List;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Contacts.ContactMethods;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.MultiAutoCompleteTextView;
import android.widget.ResourceCursorAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class AddContactActivity extends Activity {

    private static final String[] CONTACT_LIST_PROJECTION = { Imps.ContactList._ID,
                                                             Imps.ContactList.NAME, };
    private static final int CONTACT_LIST_NAME_COLUMN = 1;

    private MultiAutoCompleteTextView mAddressList;
    private Spinner mListSpinner;
    Button mInviteButton;
    Button mScanButton;
    ImApp mApp;
    SimpleAlertHandler mHandler;

    private Cursor mCursorProviders;
    private long mProviderId, mAccountId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApp = (ImApp)getApplication();
        mHandler = new SimpleAlertHandler(this);
        resolveIntent(getIntent());

        setContentView(R.layout.add_contact_activity);

        BrandingResources brandingRes = mApp.getBrandingResource(0);
        setTitle(brandingRes.getString(BrandingResourceIDs.STRING_ADD_CONTACT_TITLE));

        TextView label = (TextView) findViewById(R.id.input_contact_label);
        label.setText(brandingRes.getString(BrandingResourceIDs.STRING_LABEL_INPUT_CONTACT));

        mAddressList = (MultiAutoCompleteTextView) findViewById(R.id.email);
        mAddressList.setTokenizer(new Rfc822Tokenizer());
        mAddressList.addTextChangedListener(mTextWatcher);
        
        mListSpinner = (Spinner) findViewById(R.id.choose_list);

        mCursorProviders = queryContactLists();
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_spinner_item, mCursorProviders, new String[] { Imps.Provider.ACTIVE_ACCOUNT_USERNAME},
                new int[] { android.R.id.text1 });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        if (mCursorProviders.getCount() > 0)
        {   
            mCursorProviders.moveToFirst();
            mProviderId = mCursorProviders.getLong(PROVIDER_ID_COLUMN);
            mAccountId = mCursorProviders.getLong(ACTIVE_ACCOUNT_ID_COLUMN);
        }
        
        mListSpinner.setAdapter(adapter);
        mListSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                    int arg2, long arg3) {
                mCursorProviders.moveToPosition(arg2);
                mProviderId = mCursorProviders.getLong(PROVIDER_ID_COLUMN);
                mAccountId = mCursorProviders.getLong(ACTIVE_ACCOUNT_ID_COLUMN);
             }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }
        });
        
        
        mInviteButton = (Button) findViewById(R.id.invite);
        mInviteButton.setText(brandingRes.getString(BrandingResourceIDs.STRING_BUTTON_ADD_CONTACT));
        mInviteButton.setOnClickListener(mButtonHandler);
        mInviteButton.setEnabled(false);
        
        mScanButton = (Button) findViewById(R.id.scan);        
        mScanButton.setOnClickListener(mScanHandler);
    }

    private Cursor queryContactLists() {
        final Uri uri = Imps.Provider.CONTENT_URI_WITH_ACCOUNT;

        Cursor c = managedQuery(uri,  PROVIDER_PROJECTION,
        Imps.Provider.CATEGORY + "=?" + " AND " + Imps.Provider.ACTIVE_ACCOUNT_USERNAME + " NOT NULL" /* selection */,
        new String[] { ImApp.IMPS_CATEGORY } /* selection args */,
        Imps.Provider.DEFAULT_SORT_ORDER);
        
        return c;
    }

    private int searchInitListPos(Cursor c, String listName) {
        if (TextUtils.isEmpty(listName)) {
            return 0;
        }
        c.moveToPosition(-1);
        while (c.moveToNext()) {
            if (listName.equals(c.getString(CONTACT_LIST_NAME_COLUMN))) {
                return c.getPosition();
            }
        }
        return 0;
    }

    private void resolveIntent(Intent intent) {
       // mProviderId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID, -1);
       // mAccountId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID, -1);
    }
    
    private String getDefaultDomain ()
    {
        //mDefaultDomain = Imps.ProviderSettings.getStringValue(getContentResolver(), mProviderId,
          //      ImpsConfigNames.DEFAULT_DOMAIN);
        ContentResolver cr = getContentResolver();
        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI,new String[] {Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE},Imps.ProviderSettings.PROVIDER + "=?",new String[] { Long.toString(mProviderId)},null);
        
        Imps.ProviderSettings.QueryMap settings = new Imps.ProviderSettings.QueryMap(
                pCursor, cr, mProviderId, false /* don't keep updated */, null /* no handler */);
        
        String domain = settings.getDomain();//get domain of current user
        
        settings.close();
        
        return domain;
    }

    void inviteBuddies() {
        Rfc822Token[] recipients = Rfc822Tokenizer.tokenize(mAddressList.getText());
        try {
            IImConnection conn = mApp.getConnection(mProviderId);
            IContactList list = getContactList(conn);
            if (list == null) {
               // Log.e(ImApp.LOG_TAG, "<AddContactActivity> can't find given contact list:"
                 //                    + getSelectedListName());
                finish();
            } else {
                boolean fail = false;
                String username = null;
                
                for (Rfc822Token recipient : recipients) {
                    username = recipient.getAddress();
                    if (username.indexOf('@') == -1) {
                        username = username + "@" + getDefaultDomain();
                    }
                    if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                        log("addContact:" + username);
                    }
                    int res = list.addContact(username);
                    if (res != ImErrorInfo.NO_ERROR) {
                        fail = true;
                        mHandler.showAlert(R.string.error,
                                ErrorResUtils.getErrorRes(getResources(), res, username));
                    }
                    
                }
                // close the screen if there's no error.
                if (!fail) {
                    
                    if (username != null)
                    {
                        Intent intent=new Intent();
                        intent.putExtra("contact", username);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                    
                    
                }
            }
        } catch (RemoteException ex) {
            Log.e(ImApp.LOG_TAG, "<AddContactActivity> inviteBuddies: caught " + ex);
        }
    }

    private IContactList getContactList(IImConnection conn) {
        if (conn == null) {
            return null;
        }

        try {
            IContactListManager contactListMgr = conn.getContactListManager();
            String listName = "";//getSelectedListName(); 
            
            if (!TextUtils.isEmpty(listName)) {
                return contactListMgr.getContactList(listName);
            } else {
                // Use the default list
                List<IBinder> lists = contactListMgr.getContactLists();
                for (IBinder binder : lists) {
                    IContactList list = IContactList.Stub.asInterface(binder);
                    if (list.isDefault()) {
                        return list;
                    }
                }
                // No default list, use the first one as default list
                if (!lists.isEmpty()) {
                    return IContactList.Stub.asInterface(lists.get(0));
                }
                return null;
            }
        } catch (RemoteException e) {
            // If the service has died, there is no list for now.
            return null;
        }
    }
    
    /**
    private String getSelectedListName() {
        Cursor c = (Cursor) mListSpinner.getSelectedItem();
        return (c == null) ? null : c.getString(CONTACT_LIST_NAME_COLUMN);
    }*/

    private View.OnClickListener mButtonHandler = new View.OnClickListener() {
        public void onClick(View v) {
            mApp.callWhenServiceConnected(mHandler, new Runnable() {
                public void run() {
                    inviteBuddies();
                }
            });
        }
    };
    

    private View.OnClickListener mScanHandler = new View.OnClickListener() {
        public void onClick(View v) {
            new IntentIntegrator(AddContactActivity.this).initiateScan();

        }
    };

    private TextWatcher mTextWatcher = new TextWatcher() {
        public void afterTextChanged(Editable s) {
            mInviteButton.setEnabled(s.length() != 0);
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // noop
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // noop
        }
    };

    private static void log(String msg) {
        Log.d(ImApp.LOG_TAG, "<AddContactActivity> " + msg);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
        if (resultCode == RESULT_OK) {
            

            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode,
                    resultIntent);
            String xmppUri = scanResult.getContents();

            try
            {
    
                if (scanResult != null) {
    
                    if (xmppUri.startsWith("xmpp"))
                    {
                        Uri uriXmpp = Uri.parse(xmppUri); //strip thte scheme so we can parse it properly
                        String otrFingerprint = uriXmpp.getQueryParameter("otr-fingerprint");
                     
                        String address = uriXmpp.getUserInfo() + '@' + uriXmpp.getHost(); 
                        
                        this.mAddressList.setText(address);
                        
                        //store this for future use... ideally the user comes up as verified the first time!
                        OtrAndroidKeyManagerImpl.getInstance(this).verifyUser(address, otrFingerprint);
                        
                    }
                    
                  
                }
            }
            catch (Exception e)
            {
                Toast.makeText(this, "error parsing address: " + xmppUri,Toast.LENGTH_LONG).show();
            }
        }
    }
 
    
    private static final String[] PROVIDER_PROJECTION = {
                                                         Imps.Provider._ID,
                                                         Imps.Provider.NAME,
                                                         Imps.Provider.FULLNAME,
                                                         Imps.Provider.CATEGORY,
                                                         Imps.Provider.ACTIVE_ACCOUNT_ID,
                                                         Imps.Provider.ACTIVE_ACCOUNT_USERNAME,
                                                         Imps.Provider.ACTIVE_ACCOUNT_PW,
                                                         Imps.Provider.ACTIVE_ACCOUNT_LOCKED,
                                                         Imps.Provider.ACTIVE_ACCOUNT_KEEP_SIGNED_IN,
                                                         Imps.Provider.ACCOUNT_PRESENCE_STATUS,
                                                         Imps.Provider.ACCOUNT_CONNECTION_STATUS
                                                        };

    static final int PROVIDER_ID_COLUMN = 0;
    static final int PROVIDER_NAME_COLUMN = 1;
    static final int PROVIDER_FULLNAME_COLUMN = 2;
    static final int PROVIDER_CATEGORY_COLUMN = 3;
    static final int ACTIVE_ACCOUNT_ID_COLUMN = 4;
    static final int ACTIVE_ACCOUNT_USERNAME_COLUMN = 5;
    static final int ACTIVE_ACCOUNT_PW_COLUMN = 6;
    static final int ACTIVE_ACCOUNT_LOCKED = 7;
    static final int ACTIVE_ACCOUNT_KEEP_SIGNED_IN = 8;
    static final int ACCOUNT_PRESENCE_STATUS = 9;
    static final int ACCOUNT_CONNECTION_STATUS = 10;
}
