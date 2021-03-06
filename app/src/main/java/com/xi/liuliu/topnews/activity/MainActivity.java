package com.xi.liuliu.topnews.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WbAuthListener;
import com.sina.weibo.sdk.auth.WbConnectErrorMessage;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;
import com.xi.liuliu.topnews.R;
import com.xi.liuliu.topnews.constants.Constants;
import com.xi.liuliu.topnews.event.LiveFragmentVisibleEvent;
import com.xi.liuliu.topnews.event.LoginEvent;
import com.xi.liuliu.topnews.event.LogoutEvent;
import com.xi.liuliu.topnews.event.QQLoginEvent;
import com.xi.liuliu.topnews.event.WeiboLoginEvent;
import com.xi.liuliu.topnews.fragment.HomeFragment;
import com.xi.liuliu.topnews.fragment.LiveFragment;
import com.xi.liuliu.topnews.fragment.MineFragment;
import com.xi.liuliu.topnews.http.HttpClient;
import com.xi.liuliu.topnews.utils.DataMonitor;
import com.xi.liuliu.topnews.utils.SharedPrefUtil;
import com.xi.liuliu.topnews.utils.ToastUtil;

import org.json.JSONObject;

import java.io.IOException;

import de.greenrobot.event.EventBus;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int HOME_FRAGMENT = 0;
    private static final int LIVE_FRAGMENT = 1;
    private static final int MINE_FRAGMENT = 2;
    private HomeFragment mHomeFragment;
    private LiveFragment mLiveFragment;
    private MineFragment mMineFragment;
    private RadioGroup mRadioGroup;
    private RadioButton mHomeRadioBtn;
    private RadioButton mLiveRadioBtn;
    private RadioButton mMineRadioBtn;
    private Toast mExitToast;
    private SsoHandler mSsoHandler;
    private Tencent mTencent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        setContentView(R.layout.activity_main);
        init();
        //每次启动APP都发送一次数据，可以统计APP的启动次数
        DataMonitor.getInstance(getApplicationContext()).sendData(DataMonitor.MONITOR_TYPE_APP_START, false);
    }


    private void init() {
        mRadioGroup = (RadioGroup) findViewById(R.id.radio_group_main_activity);
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.radio_button_home_activity_main:
                        showFragment(HOME_FRAGMENT);
                        break;
                    case R.id.radio_button_live_activity_main:
                        showFragment(LIVE_FRAGMENT);
                        break;
                    case R.id.radio_button_mine_activity_main:
                        showFragment(MINE_FRAGMENT);
                        break;
                }
            }
        });
        mHomeRadioBtn = (RadioButton) findViewById(R.id.radio_button_home_activity_main);
        mLiveRadioBtn = (RadioButton) findViewById(R.id.radio_button_live_activity_main);
        mLiveRadioBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                EventBus.getDefault().post(new LiveFragmentVisibleEvent(isChecked));
            }
        });
        mMineRadioBtn = (RadioButton) findViewById(R.id.radio_button_mine_activity_main);
        boolean isLoggedIn = SharedPrefUtil.getInstance(this).getBoolean(Constants.LOGIN_SP_KEY);
        if (isLoggedIn) {
            updateBottomBarAsLogin();
        }
        //在HomeFragment加载前，先加载LiveFragment，防止启动后，第一次切换到LiveFragment时出现黑屏
        preloadLiveFragment();
        showFragment(HOME_FRAGMENT);
    }


    private void showFragment(int which) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        hideFragment(transaction);
        switch (which) {
            case HOME_FRAGMENT:
                if (mHomeFragment == null) {
                    mHomeFragment = new HomeFragment();
                    mHomeFragment.setActivity(this);
                    transaction.add(R.id.flt_fragment, mHomeFragment);
                } else {
                    transaction.show(mHomeFragment);
                }
                break;
            case LIVE_FRAGMENT:
                if (mLiveFragment == null) {
                    mLiveFragment = new LiveFragment();
                    transaction.add(R.id.flt_fragment, mLiveFragment);
                } else {
                    transaction.show(mLiveFragment);
                }
                break;
            case MINE_FRAGMENT:
                if (mMineFragment == null) {
                    mMineFragment = new MineFragment();
                    transaction.add(R.id.flt_fragment, mMineFragment);
                } else {
                    transaction.show(mMineFragment);
                }
                break;
        }
        transaction.commit();
    }

    private void hideFragment(FragmentTransaction fragmentTransaction) {
        if (fragmentTransaction != null) {
            if (mHomeFragment != null) {
                fragmentTransaction.hide(mHomeFragment);
            }
            if (mLiveFragment != null) {
                fragmentTransaction.hide(mLiveFragment);
            }
            if (mMineFragment != null) {
                fragmentTransaction.hide(mMineFragment);
            }
        }
    }

    public void onEventMainThread(LogoutEvent event) {
        if (event != null) {
            updateBottomBarAsLogout();
        }
    }

    public void onEventMainThread(LoginEvent event) {
        if (event != null) {
            updateBottomBarAsLogin();
        }
    }

    public void onEventMainThread(WeiboLoginEvent event) {
        if (event != null) {
            weiboLogin();
        }
    }

    public void onEventMainThread(QQLoginEvent event) {
        if (event != null) {
            qqLogin();
        }
    }

    private void updateBottomBarAsLogin() {
        Drawable loginDrawable = getResources().getDrawable(R.drawable.selector_main_tab_item_login);
        loginDrawable.setBounds(0, 0, loginDrawable.getMinimumWidth(), loginDrawable.getMinimumHeight());
        mMineRadioBtn.setCompoundDrawables(null, loginDrawable, null, null);
        mMineRadioBtn.setText(R.string.mine_index);
        ColorStateList color = getResources().getColorStateList(R.color.main_tab_item_color);
        mMineRadioBtn.setTextColor(color);
    }

    private void updateBottomBarAsLogout() {
        Drawable notLoginDrawable = getResources().getDrawable(R.drawable.selector_main_tab_item_mine_not_login);
        notLoginDrawable.setBounds(0, 0, notLoginDrawable.getMinimumWidth(), notLoginDrawable.getMinimumHeight());
        mMineRadioBtn.setCompoundDrawables(null, notLoginDrawable, null, null);
        mMineRadioBtn.setText(R.string.mine_index_not_login);
        ColorStateList color = getResources().getColorStateList(R.color.main_tab_item_color);
        mMineRadioBtn.setTextColor(color);
    }

    private void weiboLogin() {
        if (mSsoHandler == null) {
            mSsoHandler = new SsoHandler(this);
        }
        mSsoHandler.authorize(new WbAuthListener() {

            @Override
            public void onSuccess(final Oauth2AccessToken oauth2AccessToken) {
                Log.i(TAG, "授权成功");
                if (oauth2AccessToken.isSessionValid()) {
                    new HttpClient().setCallback(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ToastUtil.toastInCenter(MainActivity.this, R.string.mine_login_failed);
                                }
                            });
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            final String weiboUserInfo = response.body().string();
                            Log.i(TAG, "Weibo Response:" + weiboUserInfo);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ToastUtil.toastInCenter(MainActivity.this, R.string.mine_login_sucess);
                                    updateUserInfoUI(weiboUserInfo);
                                }
                            });
                        }
                    }).requestWeiboLogin(oauth2AccessToken);

                }
            }

            @Override
            public void cancel() {
                Log.i(TAG, "授权取消");
            }

            @Override
            public void onFailure(WbConnectErrorMessage wbConnectErrorMessage) {
                Log.i(TAG, "授权失败");
            }
        });
    }

    private void updateUserInfoUI(String userInfo) {
        if (userInfo != null) {
            org.json.JSONObject jsonObject;
            String name;
            String portraitUrl;
            try {
                jsonObject = new org.json.JSONObject(userInfo);
                name = jsonObject.getString("name");
                portraitUrl = jsonObject.getString("avatar_hd");
                SharedPrefUtil.getInstance(this).saveLoginStateWithWeibo(name, portraitUrl);
                EventBus.getDefault().post(new LoginEvent(LoginEvent.LOGIN_WEIBO, name, portraitUrl));

            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, "showUserInfo Exception");
            }
        }
    }

    private void qqLogin() {
        if (mTencent == null) {
            mTencent = Tencent.createInstance(Constants.QQ_APP_ID, this.getApplicationContext());
        }
        if (!mTencent.isSessionValid()) {//Session合法的话
            mTencent.login(this, "get_user_info", new IUiListener() {

                @Override
                public void onComplete(Object o) {
                    Log.i(TAG, "QQ验证成功");
                    JSONObject jsonObject = (JSONObject) o;
                    String reponse = jsonObject.toString();
                    Log.i(TAG, "QQ验证Reponse:" + reponse);
                }

                @Override
                public void onError(UiError uiError) {
                    Log.i(TAG, "QQ验证失败，code:" + uiError.errorCode + "message:" + uiError.errorMessage + "detail:" + uiError.errorDetail);
                }

                @Override
                public void onCancel() {
                    Log.i(TAG, "QQ验证取消");
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
        }
        if (mTencent != null) {
            mTencent.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mLiveFragment != null && mLiveFragment.isFullScreen()) {
                mLiveFragment.setVideoPreview();//LiveFragment横屏播放时，点击返回键，退出全屏模式
                return true;
            }
            if (mExitToast == null) {
                mExitToast = ToastUtil.getTransparentToast(this, R.string.main_activity_exit_toast, 200);
                mExitToast.show();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mExitToast != null) {
                            mExitToast.cancel();
                        }
                        mExitToast = null;
                    }
                }, 2000);
                return true;
            } else {
                return super.onKeyDown(keyCode, event);
            }
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * 设置底部导航栏的可见性，LiveFragment横屏播放时，需要隐藏导航栏
     *
     * @param visibility
     */
    public void setBottomBarVisibility(int visibility) {
        if (visibility == View.VISIBLE) {
            mRadioGroup.setVisibility(View.VISIBLE);
        } else if (visibility == View.GONE) {
            mRadioGroup.setVisibility(View.GONE);
        }
    }

    /**
     * 在HomeFragment加载前，先加载LiveFragment，防止启动后，第一次切换到LiveFragment时出现黑屏
     */
    private void preloadLiveFragment() {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        mLiveFragment = new LiveFragment();
        transaction.add(R.id.flt_fragment, mLiveFragment);
        transaction.commit();
    }
}
