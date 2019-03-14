package com.oasisfeng.island.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.oasisfeng.android.content.IntentFilters;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import java9.util.Optional;

import static android.content.Context.USER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.N_MR1;
import static java.util.Objects.requireNonNull;

/**
 * Utility class for user-related helpers. Only works within the process where this provider is declared to be running.
 *
 * Created by Oasis on 2016/9/25.
 */
public abstract class Users extends PseudoContentProvider {

	public static @Nullable UserHandle profile;		// The profile in use (semi-immutable, until profile is created or destroyed)
	public static UserHandle owner;

	public static boolean hasProfile() { return profile != null; }

	private static final UserHandle CURRENT = Process.myUserHandle();
	private static final int CURRENT_ID = toId(CURRENT);

	public static UserHandle current() { return CURRENT; }

	@Override public boolean onCreate() {
		final int priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1;
		context().registerReceiver(mProfileChangeObserver,
				IntentFilters.forActions(Intent.ACTION_MANAGED_PROFILE_ADDED, Intent.ACTION_MANAGED_PROFILE_REMOVED).inPriority(priority));
		refreshUsers(context());
		return true;
	}

	/** This method should not be called under normal circumstance. */
	public static void refreshUsers(final Context context) {
		profile = null;
		final List<UserHandle> user_and_profiles = requireNonNull((UserManager) context.getSystemService(USER_SERVICE)).getUserProfiles();
		for (final UserHandle user_or_profile : user_and_profiles) {
			if (isOwner(user_or_profile)) {
				owner = user_or_profile;
				continue;
			}
			profile = user_or_profile;
			final Optional<Boolean> is_profile_owner = DevicePolicies.isProfileOwner(context, user_or_profile);
			if (is_profile_owner == null) {
				Log.e(TAG, "Cannot detect profile owner: " + toId(user_or_profile));
				break;	// Best bet: this profile is actually managed by Island
			} else if (! is_profile_owner.isPresent()) {	// Profile has no owner, accept this profile for now and look for other profile managed by Island
				Log.i(TAG, "Profile without owner: " + toId(user_or_profile));
			} else if (! is_profile_owner.get()) {
				Log.i(TAG, "Profile not managed by Island: " + toId(user_or_profile));
			} else {
				Log.i(TAG, "Profile managed by Island: " + toId(user_or_profile));
				break;
			}
		}

		final List<UserHandle> profiles = new ArrayList<>(user_and_profiles);
		profiles.remove(owner);
		sProfiles = profiles;
		Log.i(TAG, "All profiles: " + profiles);
	}

	public static boolean isProfileRunning(final Context context, final UserHandle user) {
		if (CURRENT.equals(user)) return true;
		if (SDK_INT < N) return true;		// TODO: Alternative for pre-N ?
		final UserManager um = requireNonNull(context.getSystemService(UserManager.class));
		if (SDK_INT >= N_MR1) try {
			return um.isUserRunning(user);
		} catch (final RuntimeException e) {
			Log.w(TAG, "Error checking running state for user " + toId(user));
		}
		return um.isQuietModeEnabled(user);
	}

	public static boolean isOwner() { return CURRENT_ID == 0; }	// TODO: Support non-system primary user
	public static boolean isOwner(final UserHandle user) { return toId(user) == 0; }
	public static boolean isOwner(final int user_id) { return user_id == 0; }

	public static boolean isProfile() { return sProfiles.contains(CURRENT); }
	public static boolean isProfile(final UserHandle user) { return sProfiles.contains(user); }

	public static int toId(final UserHandle user) { return user.hashCode(); }

	public static boolean isSameApp(final int uid1, final int uid2) {
		return getAppId(uid1) == getAppId(uid2);
	}

	private static int getAppId(final int uid) {
		return uid % PER_USER_RANGE;
	}

	private final BroadcastReceiver mProfileChangeObserver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent i) {
		Log.i(TAG, "Profile changed");
		refreshUsers(context);
	}};

	private static final int PER_USER_RANGE = 100000;
	private static List<UserHandle> sProfiles = null;	// Intentionally left null to fail early if this class is accidentally used in non-default process.
	private static final String TAG = "Users";
}
