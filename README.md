# VoDMeasurementTool

Android project for analyzing the performance of different Video on Demand platforms over mobile network.
Work in progress, will be extended in the future.

The app was developed and used as part of a measurement study, published at IFIP Networking 2020:

**Measuring Decentralized Video Streaming: A Case Study of DTube** 
Trinh Viet Doan, Tat Dat Pham, Markus Oberprieler, Vaibhav Bajpai  
Technical University of Munich  
[Link to paper (PDF)](http://dl.ifip.org/db/conf/networking/networking2020/1570619852.pdf)

For the dataset and analysis scripts of the study, visit the respective [GitHub repository](https://github.com/tv-doan/ifip-net-2020-analysis).

---

## Build and run

The project can be built and run in Android Studio.

If you would like to upload the databases of the collected data to a server, edit the respective lines in `app/src/main/java/de/tum/in/cm/vodmeasurementtool/util/DbExportTask.kt`.

## Device requirements

Operating system (OS):  Android

OS version: Android 7.0 Nougat or newer

## Limitations and known issues:

- some collected metrics experimental
- only Youtube and DTube are integrated so far
- the application plays only trending videos, and randomly chosen
- scheduling of repeated measurement sessions might fail when the device changes its current time zone

## Authors

* **Tat Dat Pham** (<dat.pham@tum.de>)
* **Markus Oberprieler** (<markus.oberprieler@tum.de>)
