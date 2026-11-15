import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_color_utilities/hct/hct.dart';
import 'package:sistrum/theme/cover_palette.dart';
import 'package:sistrum/theme/mornye_theme.dart';

double _chroma(Color color) => Hct.fromInt(color.toARGB32()).chroma;

void main() {
  testWidgets('a black and white cover keeps its tonal scheme in headers', (
    tester,
  ) async {
    late String gray;
    late ColorScheme tonal;
    final cache = PaintingBinding.instance.imageCache;
    final directory = await tester.runAsync(
      () => Directory.systemTemp.createTemp('palette-gray-'),
    );
    addTearDown(() async {
      cache.clear();
      cache.clearLiveImages();
      await tester.runAsync(() => directory!.delete(recursive: true));
    });

    await tester.runAsync(() async {
      Future<String> write(String name, List<Color> stripes) async {
        final recorder = ui.PictureRecorder();
        final canvas = Canvas(recorder);
        for (var index = 0; index < stripes.length; index++) {
          canvas.drawRect(
            Rect.fromLTWH(index * 64.0 / stripes.length, 0, 64, 64),
            Paint()..color = stripes[index],
          );
        }
        final picture = recorder.endRecording();
        final image = await picture.toImage(64, 64);
        final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
        image.dispose();
        picture.dispose();
        final file = File('${directory!.path}/$name');
        await file.writeAsBytes(bytes!.buffer.asUint8List());
        return file.path;
      }

      gray = await write('gray.png', const [
        Color(0xff0a0a0a),
        Color(0xff8a8a8a),
        Color(0xfff2f2f2),
      ]);

      // No pixel clears Score's chroma filter, so the tonal scheme takes
      // Material's fallback hue; headers keep it.
      tonal = (await CoverPalette.resolve(gray, Brightness.dark))!;
      expect(_chroma(tonal.primary), greaterThan(12));
      expect(CoverPalette.peek(gray, Brightness.dark), same(tonal));
    });

    // Headers keep the tonal scheme.
    ColorScheme? built;
    await tester.pumpWidget(
      MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        home: CoverPaletteBuilder(
          imageSource: gray,
          builder: (context, scheme) {
            built = scheme;
            return const SizedBox();
          },
        ),
      ),
    );
    expect(built, same(tonal));
  });
}
