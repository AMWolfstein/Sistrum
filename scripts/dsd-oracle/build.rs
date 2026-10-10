// SPDX-License-Identifier: GPL-3.0-or-later
// Harness glue only; pinned MIT Flick source remains outside the repository.
use std::{env,fs,path::PathBuf};
fn main(){
    let root=PathBuf::from(env::var("SISTRUM_FLICK_SOURCE").expect("Run scripts/dsd-oracle.sh"));
    let module=root.join("rust/src/audio/dsd_engine/dsd/mod.rs");
    let generated=format!("#[path = {:?}] pub mod dsd;\n",module);
    fs::write(PathBuf::from(env::var("OUT_DIR").unwrap()).join("flick.rs"),generated).unwrap();
    println!("cargo:rerun-if-env-changed=SISTRUM_FLICK_SOURCE");
    for name in ["mod.rs","coefficients.rs","dop.rs"] {println!("cargo:rerun-if-changed={}",root.join("rust/src/audio/dsd_engine/dsd").join(name).display());}
}
