# BusTracker

This project is for me to get familair with GTFS feed fetching and parsing;
application itself is not very polished, and still have great potential to be improved.

--implemented functionalities:

+using Google Map, Google Play location service, custom HTTP request to fetch GTFS feeds.
+it basically fetch the Halifax bus/transit realtime data from Halifax Open Data.
+basic parsing is done, and update the position of the buses to the Google Map API every 15 seconds.
+the user can acquire its current position on the map, and move the Map's camera back to such.
+busses are shown on the map with a custom marker; its route number is also drawn on the marker (canvas on the bitmap).

--not fully implemented:

-tried to implement the functionality 
 such that it saves the Map's current Camera position even after the Activity is suspended;
 failed for unknown reason, gonna look it up in the furture.
-tried to parsing the transit Alert feed as well, failed;
 the ALert text returns null object, but the ALert IDs are successfully fetched.
 
 
 
Last modifed:
2019/11/25

Author:
Qu Yuze
