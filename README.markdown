# WaveLogger #

WaveLogger is a UC Berkeley project.

## Overview ##

WaveLogger is a companion application to
[AndroidWave](https://github.com/pjk25/AndroidWave). It is designed strictly
for testing the sensor precision limiting mechanisms in AndroidWave, through
the use of special Accelerometer and Location "pass-through" recipes. The
pass-through recipes are located at
[PassThroughRecipes](https://github.com/pjk25/PassThroughRecipes) (commit
9a18e4ea9141fa83ad08 at the time of this README).

## Design ##

WaveLogger is comprised of an Activity and a Service. The purpose of the
service is to allow background logging of data, so that multiple copies of
WaveLogger can be used simultaneously to log data at different granularities
(AndroidWave does not permit a single app to be authorized at multiple
granularities for a given recipe). The WaveLoggerService is run as a
foreground service, posting a notification at the top of the screen on the
Android device. WaveLogger logs data to an internal sqlite database, with
tables customized for the Accelerometer and Location pass-through recipes.
Data from that database can be exported to the sd card as a bundle containing
CSV files.  Location CSV data can be viewed with web/MapView.html.

## Use ##

The WaveLogger presents 4 buttons to the user, two buttons for requesting
authorization for the Accelerometer and Location pass-through recipes,
respectively, and a start and stop button for the background service.  The
menu button will allow the user to export and erase the contents of the app's
sqlite database.

In order to compile two copies of WaveLogger for simultaneous logging, one
need only adjust the package name in AndroidManifest.xml, and if desired, the
app_name string in res/values/strings.xml.