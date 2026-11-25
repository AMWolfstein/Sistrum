import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/widgets/album_detail_header.dart';

/// A header circle button by its icon. `find.byIcon` does not see the icon
/// inside a [HeaderCircleButton], so header actions are matched by the
/// button's own icon field.
Finder headerIcon(IconData icon) =>
    find.byWidgetPredicate((w) => w is HeaderCircleButton && w.icon == icon);
