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

import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Switch;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.OnMainSwitchChangeListener;

import com.xiaomi.dolby.R;

import java.util.Arrays;
import java.util.List;

public class DolbySettingsFragment extends PreferenceFragment implements
        OnPreferenceChangeListener, OnMainSwitchChangeListener {

    private static final String TAG = "XiaomiDolbySettingsFragment";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final AudioAttributes ATTRIBUTES_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();

    public static final String PREF_ENABLE = "dolby_enable";
    public static final String PREF_PROFILE = "dolby_profile";
    public static final String PREF_PRESET = "dolby_preset";
    public static final String PREF_VIRTUALIZER = "dolby_virtualizer";
    public static final String PREF_STEREO = "dolby_stereo";
    public static final String PREF_DIALOGUE = "dolby_dialogue";
    public static final String PREF_BASS = "dolby_bass";
    public static final String PREF_VOLUME = "dolby_volume";
    public static final String PREF_RESET = "dolby_reset";

    private MainSwitchPreference mSwitchBar;
    private ListPreference mProfilePref, mPresetPref, mStereoPref, mDialoguePref;
    private SwitchPreference mBassPref, mVirtualizerPref, mVolumePref;
    private Preference mResetPref;
    private CharSequence[] mPresets, mDeValues, mSwValues;

    private DolbyUtils mDolbyUtils;
    private AudioManager mAudioManager;
    private boolean mDsOn, mIsOnSpeaker;
    private int mCurrentProfile = -1;
    private final Handler mHandler = new Handler();

    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            updateSpeakerState(false);
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            updateSpeakerState(false);
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.dolby_settings);

        mAudioManager = getActivity().getSystemService(AudioManager.class);
        mDolbyUtils = DolbyUtils.getInstance(getActivity());
        mDsOn = mDolbyUtils.getDsOn();

        mSwitchBar = (MainSwitchPreference) findPreference(PREF_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(mDsOn);

        mProfilePref = (ListPreference) findPreference(PREF_PROFILE);
        mProfilePref.setOnPreferenceChangeListener(this);
        mProfilePref.setEnabled(mDsOn);

        final CharSequence[] profiles = mProfilePref.getEntryValues();
        final int profile = mDolbyUtils.getProfile();
        if (Arrays.asList(profiles).contains(Integer.toString(profile))) {
            mCurrentProfile = profile;
            mProfilePref.setSummary("%s");
            mProfilePref.setValue(Integer.toString(profile));
        } else {
            mCurrentProfile = -1;
            mProfilePref.setSummary(getActivity().getString(R.string.dolby_unknown));
        }

        mPresetPref = (ListPreference) findPreference(PREF_PRESET);
        mPresetPref.setOnPreferenceChangeListener(this);
        mPresets = mPresetPref.getEntryValues();

        mVirtualizerPref = (SwitchPreference) findPreference(PREF_VIRTUALIZER);
        mVirtualizerPref.setOnPreferenceChangeListener(this);

        mStereoPref = (ListPreference) findPreference(PREF_STEREO);
        mStereoPref.setOnPreferenceChangeListener(this);
        mSwValues = mStereoPref.getEntryValues();

        mDialoguePref = (ListPreference) findPreference(PREF_DIALOGUE);
        mDialoguePref.setOnPreferenceChangeListener(this);
        mDeValues = mDialoguePref.getEntryValues();

        mBassPref = (SwitchPreference) findPreference(PREF_BASS);
        mBassPref.setOnPreferenceChangeListener(this);

        mVolumePref = (SwitchPreference) findPreference(PREF_VOLUME);
        mVolumePref.setOnPreferenceChangeListener(this);

        mResetPref = (Preference) findPreference(PREF_RESET);
        mResetPref.setOnPreferenceClickListener(p -> {
            mDolbyUtils.resetProfileSpecificSettings();
            updateProfileSpecificPrefs();
            Toast.makeText(getActivity(),
                    getActivity().getString(R.string.dolby_reset_profile_toast,
                            mProfilePref.getSummary()), Toast.LENGTH_SHORT).show();
            return true;
        });

        mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, mHandler);
        updateSpeakerState(true);
    }

    @Override
    public void onDestroyView() {
        mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        super.onDestroyView();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case PREF_PROFILE:
                mCurrentProfile = Integer.parseInt((newValue.toString()));
                mDolbyUtils.setProfile(mCurrentProfile);
                updateProfileSpecificPrefs();
                return true;
            case PREF_PRESET:
                mDolbyUtils.setPreset(newValue.toString());
                return true;
            case PREF_VIRTUALIZER:
                if (mIsOnSpeaker)
                    mDolbyUtils.setSpeakerVirtualizerEnabled((Boolean) newValue);
                else
                    mDolbyUtils.setHeadphoneVirtualizerEnabled((Boolean) newValue);
                return true;
            case PREF_STEREO:
                mDolbyUtils.setStereoWideningAmount(Integer.parseInt((newValue.toString())));
                return true;
            case PREF_DIALOGUE:
                mDolbyUtils.setDialogueEnhancerAmount(Integer.parseInt((newValue.toString())));
                return true;
            case PREF_BASS:
                mDolbyUtils.setBassEnhancerEnabled((Boolean) newValue);
                return true;
            case PREF_VOLUME:
                mDolbyUtils.setVolumeLevelerEnabled((Boolean) newValue);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        mDsOn = isChecked;
        mDolbyUtils.setDsOn(isChecked);
        mProfilePref.setEnabled(isChecked);
        mResetPref.setEnabled(isChecked);
        updateProfileSpecificPrefs();
    }

    private void updateSpeakerState(boolean force) {
        final AudioDeviceAttributes device =
                mAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA).get(0);
        final boolean isOnSpeaker = (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        if (mIsOnSpeaker != isOnSpeaker || force) {
            if (DEBUG) Log.d(TAG, "updateSpeakerState: " + mIsOnSpeaker);
            mIsOnSpeaker = isOnSpeaker;
            updateProfileSpecificPrefs();
        }
    }

    private void updateProfileSpecificPrefs() {
        final String unknownRes = getActivity().getString(R.string.dolby_unknown);
        final String headphoneRes = getActivity().getString(R.string.dolby_connect_headphones);

        if (DEBUG) Log.d(TAG, "updateProfileSpecificPrefs: mDsOn=" + mDsOn
                + " mCurrentProfile=" + mCurrentProfile + " mIsOnSpeaker=" + mIsOnSpeaker);

        final boolean enable = mDsOn && (mCurrentProfile != -1);

        mPresetPref.setEnabled(enable);
        mVirtualizerPref.setEnabled(enable);
        mDialoguePref.setEnabled(enable);
        mVolumePref.setEnabled(enable);
        mResetPref.setEnabled(enable);

        mStereoPref.setEnabled(enable && !mIsOnSpeaker);
        mBassPref.setEnabled(enable && !mIsOnSpeaker);

        if (!enable) return;

        final String preset = mDolbyUtils.getPreset();
        if (Arrays.asList(mPresets).contains(preset)) {
            mPresetPref.setSummary("%s");
            mPresetPref.setValue(preset);
        } else {
            mPresetPref.setSummary(unknownRes);
        }

        final String deValue = Integer.toString(mDolbyUtils.getDialogueEnhancerAmount());
        if (Arrays.asList(mDeValues).contains(deValue)) {
            mDialoguePref.setSummary("%s");
            mDialoguePref.setValue(deValue);
        } else {
            mDialoguePref.setSummary(unknownRes);
        }

        mVirtualizerPref.setChecked(mIsOnSpeaker ? mDolbyUtils.getSpeakerVirtualizerEnabled()
                : mDolbyUtils.getHeadphoneVirtualizerEnabled());
        mVolumePref.setChecked(mDolbyUtils.getVolumeLevelerEnabled());

        if (mIsOnSpeaker) {
            mStereoPref.setSummary(headphoneRes);
            mBassPref.setSummary(headphoneRes);
            return;
        }

        final String swValue = Integer.toString(mDolbyUtils.getStereoWideningAmount());
        if (Arrays.asList(mSwValues).contains(swValue)) {
            mStereoPref.setSummary("%s");
            mStereoPref.setValue(swValue);
        } else {
            mStereoPref.setSummary(unknownRes);
        }

        mBassPref.setChecked(mDolbyUtils.getBassEnhancerEnabled());
        mBassPref.setSummary(null);
    }
}
