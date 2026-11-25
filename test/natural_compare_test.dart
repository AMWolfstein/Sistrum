import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/utils/natural_compare.dart';

void main() {
  test('compareNaturally orders digit runs by value', () {
    final names = ['Track 10', 'track 2', 'Disc 10', 'Track 1', 'Disc 2']
      ..sort(compareNaturally);
    expect(names, ['Disc 2', 'Disc 10', 'Track 1', 'track 2', 'Track 10']);
    expect(compareNaturally('01 Intro', '1 Intro'), isNot(0));
    expect(compareNaturally('a', 'a'), 0);
  });
}
