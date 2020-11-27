/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.emergency.action;

import static android.telecom.TelecomManager.EXTRA_CALL_SOURCE;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.emergency.R;
import com.android.emergency.widgets.countdown.CountDownAnimationView;
import com.android.emergency.widgets.slider.OnSlideCompleteListener;
import com.android.emergency.widgets.slider.SliderView;
import com.android.settingslib.emergencynumber.EmergencyNumberUtils;

import java.time.Duration;

public class EmergencyActionFragment extends Fragment implements OnSlideCompleteListener {

    private static final String TAG = "EmergencyActionFrag";
    private static final String STATE_MILLIS_LEFT = "STATE_MILLIS_LEFT";

    private MediaPlayer mMediaPlayer;
    private TelecomManager mTelecomManager;
    private CountDownTimer mCountDownTimer;
    private EmergencyNumberUtils mEmergencyNumberUtils;
    private long mCountDownMillisLeft;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mEmergencyNumberUtils = new EmergencyNumberUtils(context);
        mTelecomManager = context.getSystemService(TelecomManager.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.emergency_action_fragment, container, false);

        TextView subtitleView = view.findViewById(R.id.subtitle);
        subtitleView.setText(getString(R.string.emergency_action_subtitle,
                mEmergencyNumberUtils.getPoliceNumber()));

        SliderView cancelButton = view.findViewById(R.id.btn_cancel);
        cancelButton.setSlideCompleteListener(this);

        if (savedInstanceState != null) {
            mCountDownMillisLeft = savedInstanceState.getLong(STATE_MILLIS_LEFT);
        } else {
            mCountDownMillisLeft =
                    getResources().getInteger(R.integer.emergency_action_count_down_millis);
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        startTimer();
        playWarningSound();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_MILLIS_LEFT, mCountDownMillisLeft);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mCountDownTimer != null) {
            CountDownAnimationView countDownAnimationView =
                    getView().findViewById(R.id.count_down_view);
            countDownAnimationView.stop();
            mCountDownTimer.cancel();
        }

        stopWarningSound();
    }

    @Override
    public void onSlideComplete() {
        getActivity().finish();
    }

    private void startTimer() {
        CountDownAnimationView countDownAnimationView =
                getView().findViewById(R.id.count_down_view);

        if (mCountDownTimer != null) {
            countDownAnimationView.stop();
            mCountDownTimer.cancel();
        }

        mCountDownTimer =
                new CountDownTimer(
                        mCountDownMillisLeft,
                        getResources().getInteger(R.integer.emergency_action_count_down_interval)) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        CountDownAnimationView countDownAnimationView =
                                getView().findViewById(R.id.count_down_view);
                        if (countDownAnimationView != null) {
                            countDownAnimationView.setCountDownLeft(
                                    Duration.ofMillis(millisUntilFinished));
                        }

                        mCountDownMillisLeft = millisUntilFinished;
                    }

                    @Override
                    public void onFinish() {
                        startEmergencyCall();
                        getActivity().finish();
                    }
                };

        mCountDownTimer.start();

        countDownAnimationView.start(Duration.ofMillis(mCountDownMillisLeft));
        countDownAnimationView.showCountDown();
    }

    private boolean isPlayWarningSoundEnabled() {
        return Settings.Secure.getIntForUser(getContext().getContentResolver(),
                Settings.Secure.EMERGENCY_GESTURE_SOUND_ENABLED, 0, UserHandle.USER_CURRENT) != 0;
    }

    private void playWarningSound() {
        if (!isPlayWarningSoundEnabled()) {
            return;
        }

        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(
                    getContext(),
                    R.raw.alarm,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    /* audioSessionId= */ 0);
        }

        mMediaPlayer.setOnCompletionListener(mp -> mp.release());
        mMediaPlayer.setOnErrorListener(
                (MediaPlayer mp, int what, int extra) -> {
                    Log.w(TAG, "MediaPlayer playback failed with error code: " + what
                            + ", and extra code: " + extra);
                    mp.release();
                    return false;
                });

        mMediaPlayer.start();
    }

    private void stopWarningSound() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
    }

    private void startEmergencyCall() {
        Bundle extras = new Bundle();
        extras.putBoolean(TelecomManager.EXTRA_IS_USER_INTENT_EMERGENCY_CALL, true);
        extras.putInt(EXTRA_CALL_SOURCE, TelecomManager.CALL_SOURCE_EMERGENCY_SHORTCUT);

        mTelecomManager.placeCall(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, mEmergencyNumberUtils.getPoliceNumber(),
                        null), extras);
    }
}