// SPDX-License-Identifier: GPL-3.0-or-later
// Test-only vector producer, run inside the pinned upstream mood package.
package mood
import("encoding/json";"os";"testing")
func TestSistrumParityVectors(t *testing.T) {
 p:=Percentile{P1:0,P5:10,P50:50,P95:90,P99:100};table:=PercentileSet{}
 for _,name:=range AllFeatureNames{table[name]=p}
 rows:=make([][]float64,0,1024)
 for i:=0;i<1024;i++ {
  x:=float64(i)/4-20
  f:=RawFeatures{RMS:x,SpectralCentroid:x*.7,SpectralFlux:x*.3,Tempo:x*1.4,Crest:x*.4,OnsetVariance:x*.2,LoudnessRange:x*.5}
  rows=append(rows,[]float64{x,Normalize(x,p,sigmoidSteepness),tempoScore(x),DeriveEnergy(f,table),DeriveDanceability(f,table),DeriveBrightness(f,table),interpolatedPercentile([]float64{-32,5,17,60,201},float64(i)/1023)})
 }
 path:=os.Getenv("SISTRUM_MOOD_FIXTURES");if path==""{t.Fatal("SISTRUM_MOOD_FIXTURES required")}
 file,err:=os.Create(path);if err!=nil{t.Fatal(err)};defer file.Close()
 if err=json.NewEncoder(file).Encode(rows);err!=nil{t.Fatal(err)}
}
