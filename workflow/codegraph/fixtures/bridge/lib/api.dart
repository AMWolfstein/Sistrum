import 'package:flutter/services.dart';

const api = MethodChannel('dev.example/api');
const events = EventChannel('dev.example/events');
const computed = MethodChannel('dev.example/' + 'dynamic');
