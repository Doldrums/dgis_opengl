import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'android_event.dart';

void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MapWidget(
        width: MediaQuery.of(context).size.width.toInt(),
        height: MediaQuery.of(context).size.height.toInt());
  }
}

class MapWidget extends StatefulWidget {
  const MapWidget({super.key, required this.width, required this.height});

  final int width;
  final int height;

  @override
  MapWidgetState createState() => MapWidgetState();
}

class MapWidgetState extends State<MapWidget> {
  static const MethodChannel _mapChannel = MethodChannel('dgis_map');

  final GlobalKey _containerKey = GlobalKey();

  final AndroidMotionEventConverter _motionEventConverter =
      AndroidMotionEventConverter();

  bool _mapInitialized = false;
  int _mapTextureId = 0;

  MapWidgetState() {
    GestureBinding.instance?.pointerRouter.addGlobalRoute(_handleEvent);
  }

  @override
  initState() {
    super.initState();

    initializeMap();
  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      key: _containerKey,
      child: _mapInitialized ? Texture(textureId: _mapTextureId) : null,
    );
  }

  Future<void> initializeMap() async {
    _mapTextureId = await _mapChannel.invokeMethod('create', {
      'width': widget.width,
      'height': widget.height,
    });
    print("DGis map textureId=$_mapTextureId");
    _mapInitialized = true;
    setState(() {});
  }

  void _handleEvent(PointerEvent event) {
    var __rb = _containerKey.currentContext?.findRenderObject();
    if (__rb == null) {
      print("RenderBox not Found");
      return;
    }
    var _rb = __rb as RenderBox;

    _motionEventConverter.pointTransformer = __rb.globalToLocal;

    if (event is PointerHoverEvent) {
      return;
    }

    if (event is PointerDownEvent) {
      _motionEventConverter.handlePointerDownEvent(event);
    }

    _motionEventConverter.updatePointerPositions(event);

    final AndroidMotionEvent? androidEvent =
        _motionEventConverter.toAndroidMotionEvent(event);

    if (event is PointerUpEvent) {
      _motionEventConverter.handlePointerUpEvent(event);
    } else if (event is PointerCancelEvent) {
      _motionEventConverter.handlePointerCancelEvent(event);
    }

    if (androidEvent != null) {
      sendMotionEvent(androidEvent);
    }
  }

  sendMotionEvent(AndroidMotionEvent androidEvent) {
    _mapChannel.invokeMethod('touch', androidEvent.asList());
  }
}
