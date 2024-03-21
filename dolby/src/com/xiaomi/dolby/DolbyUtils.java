/*
 * Copyright (C) 2018,2020 The LineageOS Project
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

package com.xiaomi.dolby;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioManager.AudioPlaybackCallback;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.xiaomi.dolby.R;
import com.xiaomi.dolby.DolbyConstants.DsParam;

import java.util.Arrays;
import java.util.List;

public final class DolbyUtils {

    private static final String TAG = "XiaomiDolbyUtils";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int EFFECT_PRIORITY = 100;
    private static final int VOLUME_LEVELER_AMOUNT = 2;

    private static final AudioAttributes ATTRIBUTES_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();

    private static DolbyUtils mInstance;
    private DolbyAtmos mDolbyAtmos;
    private Context mContext;
    private AudioManager mAudioManager;
    private Handler mHandler = new Handler();
    private boolean mCallbacksRegistered = false;

    // Restore current profile on every media session
    private final AudioPlaybackCallback mPlaybackCallback = new AudioPlaybackCallback() {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            boolean isPlaying = configs.stream().anyMatch(
                    c -> c.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED);
            if (DEBUG) Log.d(TAG, "onPlaybackConfigChanged isPlaying=" + isPlaying);
            if (isPlaying)
                setCurrentProfile();
        }
    };

    // Restore current profile on audio device change
    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (DEBUG) Log.d(TAG, "onAudioDevicesAdded");
            setCurrentProfile();
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (DEBUG) Log.d(TAG, "onAudioDevicesRemoved");
            setCurrentProfile();
        }
    };

    private DolbyUtils(Context context) {
        mContext = context;
        mDolbyAtmos = new DolbyAtmos(EFFECT_PRIORITY, 0);
        mAudioManager = context.getSystemService(AudioManager.class);
        if (DEBUG) Log.d(TAG, "initalized");
    }

    public static synchronized DolbyUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DolbyUtils(context);
        }
        return mInstance;
    }

    public void onBootCompleted() {
        if (DEBUG) Log.d(TAG, "onBootCompleted");

        // Restore current profile now and on certain audio changes.
        final boolean dsOn = getDsOn();
        mDolbyAtmos.setEnabled(dsOn);
        registerCallbacks(dsOn);
        if (dsOn)
            setCurrentProfile();

        // Restore speaker virtualizer, because for some reason it isn't
        // enabled automatically at boot.
        final AudioDeviceAttributes device =
                mAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA).get(0);
        final boolean isOnSpeaker = (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        final boolean spkVirtEnabled = getSpeakerVirtualizerEnabled();
        if (DEBUG) Log.d(TAG, "isOnSpeaker=" + isOnSpeaker + " spkVirtEnabled=" + spkVirtEnabled);
        if (isOnSpeaker && spkVirtEnabled) {
            setSpeakerVirtualizerEnabled(false);
            setSpeakerVirtualizerEnabled(true);
            if (DEBUG) Log.d(TAG, "re-enabled speaker virtualizer");
        }
    }

    private void checkEffect() {
        if (!mDolbyAtmos.hasControl()) {
            Log.w(TAG, "lost control, recreating effect");
            mDolbyAtmos.release();
            mDolbyAtmos = new DolbyAtmos(EFFECT_PRIORITY, 0);
        }
    }

    private void setCurrentProfile() {
        if (!getDsOn()) {
            if (DEBUG) Log.d(TAG, "setCurrentProfile: skip, dolby is off");
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int profile = Integer.parseInt(prefs.getString(
                DolbySettingsFragment.PREF_PROFILE, "0" /*dynamic*/));
        setProfile(profile);
    }

    private void registerCallbacks(boolean register) {
        if (DEBUG) Log.d(TAG, "registerCallbacks(" + register + ") mCallbacksRegistered=" + mCallbacksRegistered);
        if (register && !mCallbacksRegistered) {
            mAudioManager.registerAudioPlaybackCallback(mPlaybackCallback, mHandler);
            mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, mHandler);
            mCallbacksRegistered = true;
        } else if (!register && mCallbacksRegistered) {
            mAudioManager.unregisterAudioPlaybackCallback(mPlaybackCallback);
            mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
            mCallbacksRegistered = false;
        }
    }

    public void setDsOn(boolean on) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setDsOn: " + on);
        mDolbyAtmos.setDsOn(on);
        registerCallbacks(on);
        if (on)
            setCurrentProfile();
    }

    public boolean getDsOn() {
        boolean on = mDolbyAtmos.getDsOn();
        if (DEBUG) Log.d(TAG, "getDsOn: " + on);
        return on;
    }

    public void setProfile(int index) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setProfile: " + index);
        mDolbyAtmos.setProfile(index);
    }

    public int getProfile() {
        int profile = mDolbyAtmos.getProfile();
        if (DEBUG) Log.d(TAG, "getProfile: " + profile);
        return profile;
    }

    public String getProfileName() {
        String profile = Integer.toString(mDolbyAtmos.getProfile());
        List<String> profiles = Arrays.asList(mContext.getResources().getStringArray(
                R.array.dolby_profile_values));
        int profileIndex = profiles.indexOf(profile);
        if (DEBUG) Log.d(TAG, "getProfileName: profile=" + profile + " index=" + profileIndex);
        return profileIndex == -1 ? null : mContext.getResources().getStringArray(
                R.array.dolby_profile_entries)[profileIndex];
    }

    public void resetProfileSpecificSettings() {
        checkEffect();
        mDolbyAtmos.resetProfileSpecificSettings();
    }

    public void setPreset(String preset) {
        checkEffect();
        int[] gains = Arrays.stream(preset.split(",")).mapToInt(Integer::parseInt).toArray();
        if (DEBUG) Log.d(TAG, "setPreset: " + Arrays.toString(gains));
        mDolbyAtmos.setDapParameter(DsParam.GEQ_BAND_GAINS, gains);
    }

    public String getPreset() {
        int[] gains = mDolbyAtmos.getDapParameter(DsParam.GEQ_BAND_GAINS);
        if (DEBUG) Log.d(TAG, "getPreset: " + Arrays.toString(gains));
        String[] preset = Arrays.stream(gains).mapToObj(String::valueOf).toArray(String[]::new);
        return String.join(",", preset);
    }

    public void setHeadphoneVirtualizerEnabled(boolean enable) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setHeadphoneVirtualizerEnabled: " + enable);
        mDolbyAtmos.setDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, enable);
    }

    public boolean getHeadphoneVirtualizerEnabled() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER);
        if (DEBUG) Log.d(TAG, "getHeadphoneVirtualizerEnabled: " + enabled);
        return enabled;
    }

    public void setSpeakerVirtualizerEnabled(boolean enable) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setSpeakerVirtualizerEnabled: " + enable);
        mDolbyAtmos.setDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, enable);
    }

    public boolean getSpeakerVirtualizerEnabled() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER);
        if (DEBUG) Log.d(TAG, "getSpeakerVirtualizerEnabled: " + enabled);
        return enabled;
    }

    public void setStereoWideningAmount(int amount) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setStereoWideningAmount: " + amount);
        mDolbyAtmos.setDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, amount);
    }

    public int getStereoWideningAmount() {
        int amount = mDolbyAtmos.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT);
        if (DEBUG) Log.d(TAG, "getStereoWideningAmount: " + amount);
        return amount;
    }

    public void setDialogueEnhancerAmount(int amount) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setDialogueEnhancerAmount: " + amount);
        mDolbyAtmos.setDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, amount > 0);
        mDolbyAtmos.setDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, amount);
    }

    public int getDialogueEnhancerAmount() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(
                DsParam.DIALOGUE_ENHANCER_ENABLE);
        int amount = enabled ? mDolbyAtmos.getDapParameterInt(
                DsParam.DIALOGUE_ENHANCER_AMOUNT) : 0;
        if (DEBUG) Log.d(TAG, "getDialogueEnhancerAmount: " + enabled + " amount=" + amount);
        return amount;
    }

    public void setBassEnhancerEnabled(boolean enable) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setBassEnhancerEnabled: " + enable);
        mDolbyAtmos.setDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, enable);
    }

    public boolean getBassEnhancerEnabled() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE);
        if (DEBUG) Log.d(TAG, "getBassEnhancerEnabled: " + enabled);
        return enabled;
    }

    public void setVolumeLevelerEnabled(boolean enable) {
        checkEffect();
        if (DEBUG) Log.d(TAG, "setVolumeLevelerEnabled: " + enable);
        mDolbyAtmos.setDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, enable);
        mDolbyAtmos.setDapParameterInt(DsParam.VOLUME_LEVELER_AMOUNT,
                enable ? VOLUME_LEVELER_AMOUNT : 0);
    }

    public boolean getVolumeLevelerEnabled() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE);
        int amount = mDolbyAtmos.getDapParameterInt(DsParam.VOLUME_LEVELER_AMOUNT);
        if (DEBUG) Log.d(TAG, "getVolumeLevelerEnabled: " + enabled + " amount=" + amount);
        return enabled && (amount == VOLUME_LEVELER_AMOUNT);
    }
}
