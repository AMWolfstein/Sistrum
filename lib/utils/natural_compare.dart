/// Orders names as a file manager does: case-insensitive, digit runs by
/// their value, so "Disc 2" comes before "Disc 10".
bool _isDigit(int unit) => unit >= 0x30 && unit <= 0x39;

int compareNaturally(String a, String b) {
  final left = a.toLowerCase();
  final right = b.toLowerCase();
  var i = 0;
  var j = 0;
  while (i < left.length && j < right.length) {
    final x = left.codeUnitAt(i);
    final y = right.codeUnitAt(j);
    if (_isDigit(x) && _isDigit(y)) {
      while (i < left.length - 1 &&
          left.codeUnitAt(i) == 0x30 &&
          _isDigit(left.codeUnitAt(i + 1))) {
        i++;
      }
      while (j < right.length - 1 &&
          right.codeUnitAt(j) == 0x30 &&
          _isDigit(right.codeUnitAt(j + 1))) {
        j++;
      }
      var iEnd = i;
      while (iEnd < left.length && _isDigit(left.codeUnitAt(iEnd))) {
        iEnd++;
      }
      var jEnd = j;
      while (jEnd < right.length && _isDigit(right.codeUnitAt(jEnd))) {
        jEnd++;
      }
      final length = (iEnd - i) - (jEnd - j);
      if (length != 0) return length;
      final digits = left
          .substring(i, iEnd)
          .compareTo(right.substring(j, jEnd));
      if (digits != 0) return digits;
      i = iEnd;
      j = jEnd;
      continue;
    }
    if (x != y) return x - y;
    i++;
    j++;
  }
  final rest = (left.length - i) - (right.length - j);
  return rest != 0 ? rest : a.compareTo(b);
}
