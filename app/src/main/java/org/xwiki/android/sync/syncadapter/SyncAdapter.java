/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.android.sync.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;
import org.xwiki.android.sync.AppContext;
import org.xwiki.android.sync.Constants;
import org.xwiki.android.sync.bean.XWikiUserFull;
import org.xwiki.android.sync.contactdb.ContactManager;
import org.xwiki.android.sync.rest.XWikiHttp;
import org.xwiki.android.sync.utils.SharedPrefsUtils;
import org.xwiki.android.sync.utils.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Future;

import rx.Observable;
import rx.Observer;
import rx.functions.Action0;
import rx.subjects.PublishSubject;

/**
 * SyncAdapter
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "SyncAdapter";
    private static final boolean NOTIFY_AUTH_FAILURE = true;

    private final AccountManager mAccountManager;
    private final Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(final Account account, Bundle extras, String authority,
                              ContentProviderClient provider, final SyncResult syncResult) {
        Log.i(TAG, "onPerformSync start");
        int syncType = SharedPrefsUtils.getValue(mContext, Constants.SYNC_TYPE, Constants.SYNC_TYPE_NO_NEED_SYNC);
        Log.i(TAG, "syncType=" + syncType);
        if (syncType == Constants.SYNC_TYPE_NO_NEED_SYNC) return;
        // get last sync date. return new Date(0) if first onPerformSync
        String lastSyncMarker = getServerSyncMarker(account);
        Log.d(TAG, lastSyncMarker);
        // By default, contacts from a 3rd party provider are hidden in the contacts
        // list. So let's set the flag that causes them to be visible, so that users
        // can actually see these contacts. date format: "1980-09-24T19:45:31+02:00"
        if (lastSyncMarker.equals(StringUtils.dateToIso8601String(new Date(0)))) {
            ContactManager.setAccountContactsVisibility(getContext(), account, true);
        }

        //TODO may need to check authToken, or block other's getAuthToken.
        //final String authtoken = mAccountManager.blockingGetAuthToken(account,
        //        AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS, NOTIFY_AUTH_FAILURE);


        // Get XWiki SyncData from XWiki server , which should be added, updated or deleted after lastSyncMarker.
        final Observable<XWikiUserFull> observable = XWikiHttp.getSyncData(syncType);

        // Update the local contacts database with the last modified changes. updateContact()
        ContactManager.updateContacts(mContext, account.name, observable);

        final Object[] sync = new Object[]{null};

        observable.subscribe(
            new Observer<XWikiUserFull>() {
                @Override
                public void onCompleted() {
                    // Save off the new sync date. On our next sync, we only want to receive
                    // contacts that have changed since this sync...
                    setServerSyncMarker(account, StringUtils.dateToIso8601String(new Date()));
                    synchronized (sync) {
                        sync[0] = new Object();
                        sync.notifyAll();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    syncResult.stats.numIoExceptions++;
                    synchronized (sync) {
                        sync[0] = new Object();
                        sync.notifyAll();
                    }
                }

                @Override
                public void onNext(XWikiUserFull xWikiUserFull) {
                    syncResult.stats.numEntries++;
                }
            }
        );

        synchronized (observable) {
            observable.notifyAll();
        }
        synchronized (sync) {
            while (sync[0] == null) {
                try {
                    sync.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Can't await end of sync", e);
                }
            }
        }
    }

    /**
     * This helper function fetches the last known high-water-mark
     * we received from the server - or 0 if we've never synced.
     *
     * @param account the account we're syncing
     * @return the change high-water-mark  Iso8601
     */
    private String getServerSyncMarker(Account account) {
        String lastSyncIso = mAccountManager.getUserData(account, Constants.SYNC_MARKER_KEY);
        //if empty, just return new Date(0) so that we can get all users from server.
        if (TextUtils.isEmpty(lastSyncIso)) {
            return StringUtils.dateToIso8601String(new Date(0));
        }
        return lastSyncIso;
    }

    /**
     * Save off the high-water-mark we receive back from the server.
     *
     * @param account     The account we're syncing
     * @param lastSyncIso The high-water-mark we want to save.
     */
    private void setServerSyncMarker(Account account, String lastSyncIso) {
        mAccountManager.setUserData(account, Constants.SYNC_MARKER_KEY, lastSyncIso);
    }
}
