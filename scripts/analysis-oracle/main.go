// SPDX-License-Identifier: GPL-3.0-or-later
// Desktop harness for misa198/Airmedy 7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1.
package main
/*
#include <stdlib.h>
#include "ffmpeg_analyzer.h"
*/
import "C"
import("encoding/json";"fmt";"math";"os";"unsafe")
func number(v C.double) interface{} { d:=float64(v); if math.IsNaN(d){return "NaN"}; if math.IsInf(d,1){return "+Infinity"}; if math.IsInf(d,-1){return "-Infinity"}; return d }
func main(){
 for _,path:=range os.Args[1:] {
  p:=C.CString(path); var r C.FFAnalysisResult
  rc:=C.ffmpeg_analyze(p,&r,nil,C.FFA_COMPONENT_FFMPEG|C.FFA_COMPONENT_AUBIO); C.free(unsafe.Pointer(p))
  if rc!=0 {fmt.Fprintf(os.Stderr,"%s: analyzer error %d\n",path,int(rc));os.Exit(1)}
  out:=map[string]interface{}{"file":path,"loudness_lufs":number(r.loudness_lufs),"loudness_range":number(r.loudness_range),"true_peak":number(r.true_peak),"rms":number(r.rms),"crest":number(r.crest),"spectral_centroid":number(r.spectral_centroid),"spectral_rolloff":number(r.spectral_rolloff),"spectral_flatness":number(r.spectral_flatness),"spectral_flux":number(r.spectral_flux),"zcr":number(r.zcr),"tempo":number(r.tempo),"onset_variance":number(r.onset_variance)}
  if err:=json.NewEncoder(os.Stdout).Encode(out);err!=nil{panic(err)}
 }
}
