use rustls::ClientConfig;
use rustls::pki_types::CertificateDer;
use std::io;
use std::sync::Arc;

pub(crate) fn configuration(extra_roots: &[CertificateDer<'static>]) -> io::Result<ClientConfig> {
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let builder = ClientConfig::builder_with_provider(Arc::clone(&provider))
        .with_safe_default_protocol_versions()
        .map_err(io::Error::other)?;
    let supplemental = rustls_pemfile::certs(&mut include_bytes!("roots.pem").as_slice())
        .collect::<Result<Vec<_>, _>>()?;

    let builder = {
        let load = || {
            let mut roots = rustls::RootCertStore::empty();
            roots.add_parsable_certificates(rustls_native_certs::load_native_certs().certs);
            #[cfg(target_os = "android")]
            for directory in [
                "/system/etc/security/cacerts",
                "/data/misc/keychain/certs-added",
                "/apex/com.android.conscrypt/cacerts",
            ] {
                if let Ok(entries) = std::fs::read_dir(directory) {
                    for entry in entries.flatten() {
                        if let Ok(pem) = std::fs::read(entry.path()) {
                            roots.add_parsable_certificates(
                                rustls_pemfile::certs(&mut pem.as_slice()).flatten(),
                            );
                        }
                    }
                }
            }
            roots.add_parsable_certificates(supplemental);
            Arc::new(roots)
        };
        // Match Go's process-wide system CA snapshot on Android. Only immutable
        // trust anchors are shared; caller-supplied roots never enter this cache.
        #[cfg(target_os = "android")]
        let roots = {
            static ROOTS: std::sync::OnceLock<Arc<rustls::RootCertStore>> =
                std::sync::OnceLock::new();
            Arc::clone(ROOTS.get_or_init(load))
        };
        #[cfg(not(target_os = "android"))]
        let roots = load();
        let roots = if extra_roots.is_empty() {
            roots
        } else {
            let mut scoped = (*roots).clone();
            scoped.add_parsable_certificates(extra_roots.iter().cloned());
            Arc::new(scoped)
        };
        builder.with_root_certificates(roots)
    };
    let mut config = builder.with_no_client_auth();
    config.resumption = rustls::client::Resumption::in_memory_sessions(64);
    Ok(config)
}
