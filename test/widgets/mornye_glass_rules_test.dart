import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/providers/runtime_profile_provider.dart';
import 'package:mornye/widgets/mornye_liquid_backdrop.dart';

void main() {
  test('liquid glass uses the fixed Clear blur', () {
    expect(MornyeGlassRules.blurSigma(MornyeGlassLevel.liquid), 2.0);
    expect(MornyeGlassRules.clearSigma, 2.0);
  });

  test('frosted glass keeps its small fallback budget', () {
    expect(MornyeGlassRules.blurSigma(MornyeGlassLevel.frosted), 3.0);
    expect(MornyeGlassRules.frostedSigma, 3.0);
  });

  test('each level shares one backdrop filter', () {
    expect(
      identical(
        MornyeGlassRules.blurFilter(MornyeGlassLevel.liquid),
        MornyeGlassRules.blurFilter(MornyeGlassLevel.liquid),
      ),
      isTrue,
    );
    expect(
      MornyeGlassRules.blurFilter(MornyeGlassLevel.liquid),
      isNot(MornyeGlassRules.blurFilter(MornyeGlassLevel.frosted)),
    );
  });

  test('dispersion scales with the effect amount only', () {
    expect(MornyeGlassRules.dispersion(1), closeTo(1.32, 1e-9));
    expect(MornyeGlassRules.dispersion(0.5), closeTo(0.66, 1e-9));
    expect(MornyeGlassRules.dispersion(0), 0);
  });
}
