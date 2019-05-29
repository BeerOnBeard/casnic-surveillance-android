# Casnic Surveillance Android Application

A simple RTSP server that uses the phone's camera and microphone. Specifically targeting an LG G3 running Android 6.0.

## How To Start

1. Connect physical phone to same network as the host machine that will run VLC
1. Start app from Android Studio
1. Start stream in VLC
  - Open VLC
  - Media => Open Network Stream... => `rtsp://username:password@xxx.xxx.xxx.xxx:8086` (using IP of phone)

## Authorization

Default values of `admin:changeit` are set in `secrets.xml` in the values folder of the application. Data is passed into the `RtspService` via extended intent data in `MainActivity.java. I highly recommend changing these if you decide to deploy this. I know I have.

## Acknowledgements

Uses code from [fyhertz/libstreaming](https://github.com/fyhertz/libstreaming). The Apache License 2.0 from that repository has been maintained in this one according the conditions outlined in the license. A significant portion of the original code has been deleted because it was not needed for this very specific project. Also, variable names and formatting was changed to make it easier for me to read as I refactored. The entire set of changes can been found in the commit history.
