# USB Cinema Fire TV

Android-TV/Fire-TV client prototype for the USB Cinema Python server.

## Build
GitHub Actions builds a debug APK on pushes and from Actions > Build USB Cinema APK > Run workflow.

## Current prototype
- Movie list from `/api/movies`
- HTTP health check
- Manual server address
- Basic candidate-server checks
- Media3 video playback
- Fire TV remote navigation

## Important limitations
- Automatic discovery currently checks a few common addresses; it is not yet a full subnet/mDNS discovery implementation.
- Local cover loading and USB-stick cover delivery need a matching cover endpoint in the Python server.
- The current Python server does not enforce app authentication. Do not expose it to the public internet. Add server-side token validation before treating access as protected.
- Test the APK on the target Fire TV; codec support depends on the device and video file.
