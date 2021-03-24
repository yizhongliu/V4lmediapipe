package pri.tool.v4lmediapipe;

import android.app.Activity;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHelper {

    private static final String TAG = "PermissionHelper";
    private static final String AUDIO_PERMISSION = "android.permission.RECORD_AUDIO";
    private static final String CAMERA_PERMISSION = "android.permission.CAMERA";
    private static final int REQUEST_CODE = 0;

    public PermissionHelper() {
    }

    public static boolean permissionsGranted(Activity context, String[] permissions) {
        String[] var2 = permissions;
        int var3 = permissions.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            String permission = var2[var4];
            int permissionStatus = ContextCompat.checkSelfPermission(context, permission);
            if (permissionStatus != 0) {
                return false;
            }
        }

        return true;
    }

    public static void checkAndRequestPermissions(Activity context, String[] permissions) {
        if (!permissionsGranted(context, permissions)) {
            ActivityCompat.requestPermissions(context, permissions, 0);
        }

    }

    public static boolean cameraPermissionsGranted(Activity context) {
        return permissionsGranted(context, new String[]{"android.permission.CAMERA"});
    }

    public static void checkAndRequestCameraPermissions(Activity context) {
        Log.d("PermissionHelper", "checkAndRequestCameraPermissions");
        checkAndRequestPermissions(context, new String[]{"android.permission.CAMERA"});
    }

    public static boolean audioPermissionsGranted(Activity context) {
        return permissionsGranted(context, new String[]{"android.permission.RECORD_AUDIO"});
    }

    public static void checkAndRequestAudioPermissions(Activity context) {
        Log.d("PermissionHelper", "checkAndRequestAudioPermissions");
        checkAndRequestPermissions(context, new String[]{"android.permission.RECORD_AUDIO"});
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d("PermissionHelper", "onRequestPermissionsResult");
        if (permissions.length > 0 && grantResults.length != permissions.length) {
            Log.d("PermissionHelper", "Permission denied.");
        } else {
            for (int i = 0; i < grantResults.length; ++i) {
                if (grantResults[i] == 0) {
                    Log.d("PermissionHelper", permissions[i] + " permission granted.");
                }
            }

        }
    }
}
