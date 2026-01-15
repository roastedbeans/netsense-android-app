# SDK 36 Update - NetMonster Core Compatibility

## Overview
Updated the app to Android SDK 36 to align with the latest NetMonster Core library updates as referenced in [NetMonster Core repository](https://github.com/mroczis/netmonster-core).

## Changes Made

### 1. SDK Version Updates

#### App Module (`app/build.gradle.kts`)
- **compileSdk**: Updated from `35` → `36`
- **targetSdk**: Updated from `35` → `36`
- **minSdk**: Remains `24` (unchanged)

#### Library Module (`library/build.gradle`)
- **compileSdk**: Updated from `35` → `36`
- **targetSdkVersion**: Updated from `35` → `36`
- **minSdkVersion**: Remains `21` (unchanged)

### 2. Build Status
✅ **Build Successful** - App compiles successfully with SDK 36

### 3. Deprecation Warnings (Expected)
The following deprecation warnings are present but expected:

- `CellInfoCdma` is deprecated (CDMA networks are being phased out)
- CDMA-related APIs are deprecated in favor of LTE/5G NR

These warnings don't affect functionality and are expected as CDMA networks are being sunset globally.

## Compatibility Notes

### NetMonster Core Integration
According to the [NetMonster Core documentation](https://github.com/mroczis/netmonster-core):
- NetMonster Core provides enhanced telephony capabilities
- Supports multiple data sources: `ALL_CELL_INFO`, `CELL_LOCATION`, `SIGNAL_STRENGTH`, `NETWORK_REGISTRATION_INFO`
- Validates and corrects data from RIL (Radio Interface Layer)
- Backports features to older Android devices

### Android 15 (API 35/36) Considerations
- Cell information access may have additional restrictions
- Both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions are required
- `READ_PHONE_STATE` permission is essential
- Some APIs may return empty results due to privacy restrictions

## Testing Recommendations

1. **Permission Testing**
   - Verify both location permissions are granted
   - Test on devices with and without SIM cards
   - Test airplane mode behavior

2. **Cell Information Collection**
   - Test on Android 15 devices
   - Verify serving cell detection
   - Verify neighboring cell detection
   - Check RSRP/RSSI signal strength reporting

3. **Compatibility Testing**
   - Test on Android 10+ devices (API 29+)
   - Test on Android 15 devices (API 35/36)
   - Verify fallback mechanisms work correctly

## Next Steps

1. ✅ SDK 36 update complete
2. ⏳ Test on Android 15 device
3. ⏳ Monitor for any runtime issues
4. ⏳ Consider updating NetMonster Core dependency if using external library

## References

- [NetMonster Core Repository](https://github.com/mroczis/netmonster-core)
- [Android SDK Platform Release Notes](https://developer.android.com/studio/releases/platforms)
- [Android 15 Behavior Changes](https://developer.android.com/about/versions/15/behavior-changes-15)

