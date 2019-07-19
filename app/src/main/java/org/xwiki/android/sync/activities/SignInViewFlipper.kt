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
package org.xwiki.android.sync.activities

import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.View
import androidx.databinding.DataBindingUtil
import org.xwiki.android.sync.R
import org.xwiki.android.sync.auth.AuthenticatorActivity
import org.xwiki.android.sync.auth.PARAM_USER_PASS
import org.xwiki.android.sync.rest.XWikiHttp
import org.xwiki.android.sync.utils.decrement
import org.xwiki.android.sync.utils.increment
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

/**
 * Tag for logging.
 */
private val TAG = "SignInViewFlipper"

/**
 * Main auth flipper.
 *
 * @version $Id: cc56e12004e2982133afa0c17258b164b8203a15 $
 */
/**
 * Standard constructor
 *
 * @param activity Current activity
 * @param contentRootView Root view of this flipper
 */

class SignInViewFlipper(activity: AuthenticatorActivity, contentRootView: View)
    : BaseViewFlipper(activity, contentRootView) {

    /**
     * Typed username.
     */
    private lateinit var accountName: String

    /**
     * Typed password.
     */
    private lateinit var accountPassword: String

    /**
     * Databinding of this flipper
     */

    lateinit var binding : org.xwiki.android.sync.databinding.ViewflipperSigninBinding

    init {
        binding = DataBindingUtil.setContentView(mActivity, R.layout.viewflipper_signin)
        binding.signInButton.setOnClickListener {
            if (checkInput()) {
                increment()
                mActivity.showProgress(
                    mContext.getText(R.string.sign_in_authenticating),
                    submit()
                )
            }
        }
    }

    /**
     * Calling when user push "login".
     */
    override fun doNext() {}

    /**
     * Return to setting server ip address, calling by pressing "back".
     */
    override fun doPrevious() {}

    /**
     * @return true if current input correct and variables [.accountName] and
     * [.accountPassword] was correctly set
     */
    private fun checkInput(): Boolean {
         return binding.accountPassword.let { field ->
             accountPassword = field.text.toString()
            field.error = null
            when {
                TextUtils.isEmpty(field.text) || field.length() < 5 -> {
                    field.requestFocus()
                    field.error = mContext.getString(R.string.error_invalid_password)
                    false
                }
                else -> {
                    field.error = null
                    true
                }

            }
        } && binding.accountName.let { field ->
             field.error = null
             accountName = field.text.toString()
             when {
                 TextUtils.isEmpty(field.text) -> {
                     field.requestFocus()
                     field.error = mContext.getString(R.string.error_field_required)
                     false
                 } else -> {
                             field.error = null
                             true
                         }

             }
         }
    }

    /**
     * Start login procedure.
     *
     * @return Subscription which can be unsubscribed for preventing log in if user cancel it
     */
    private fun submit(): Subscription {
        val userName = accountName
        val userPass = accountPassword

        return XWikiHttp.login(
            userName,
            userPass
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { authtoken ->
                    mActivity.hideProgress()
                    if (authtoken == null) {
                        showErrorMessage(mContext.getString(R.string.loginError))
                    } else {
                        signedIn(
                            authtoken,
                            userName,
                            userPass
                        )
                    }
                },
                {
                    mActivity.hideProgress()
                    showErrorMessage(mContext.getString(R.string.loginError))
                }
            )
    }

    /**
     * Prepare log in intent which will contains credentials and other data.
     *
     * @param authtoken Authtoken (or session cookie) which was set by response
     * @param username Account username to save
     * @param password Account password to save
     * @return Prepared intent
     *
     * @since 0.4
     */
    private fun prepareIntent(
        authtoken: String,
        username: String,
        password: String
    ): Intent {
        val accountType = mActivity.intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)

        val data = Bundle()
        data.putString(AccountManager.KEY_ACCOUNT_NAME, username)
        data.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType)
        data.putString(AccountManager.KEY_AUTHTOKEN, authtoken)
        data.putString(PARAM_USER_PASS, password)

        val intent = Intent()
        intent.putExtras(data)
        return intent
    }

    /**
     * Must be called if user successfully logged in.
     *
     * @param authtoken Authtoken (or session cookie) which was set by response
     * @param username Account username to save
     * @param password Account password to save
     *
     * @since 0.4
     */
    private fun signedIn(
        authtoken: String,
        username: String,
        password: String
    ) {
        val signedIn = prepareIntent(
            authtoken,
            username,
            password
        )

        mActivity.runOnUiThread {
            mActivity.hideProgress()
            mActivity.finishLogin(signedIn)
            mActivity.hideInputMethod()
            mActivity.doNext(mContentRootView)
        }
    }

    /**
     * Must be called to show user that something went wrong.
     *
     * @param error String which must be shown in error message
     */
    private fun showErrorMessage(error: String) {
        val errorTextView = binding.errorMsg
        errorTextView.visibility = View.VISIBLE
        errorTextView.text = error
        Handler().postDelayed({
            errorTextView.visibility = View.GONE
            decrement()
        },
            2000
        )
    }

}
