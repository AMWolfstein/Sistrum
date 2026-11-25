//! Explicit-content advisory read from each common container's tag layout.
//! Each fixture is a minimal synthetic file with one title and the advisory.

use sistrum_core::tags::read_audio_tags;
use std::io::Cursor;

fn atom(kind: &[u8; 4], body: &[u8]) -> Vec<u8> {
    let mut result = ((body.len() + 8) as u32).to_be_bytes().to_vec();
    result.extend(kind);
    result.extend(body);
    result
}

fn data_atom(value: &[u8]) -> Vec<u8> {
    let mut body = vec![0; 8];
    body.extend(value);
    atom(b"data", &body)
}

fn mp4_with_rtng(value: u8) -> Vec<u8> {
    let mut ilst = atom(b"\xa9nam", &data_atom(b"Title"));
    ilst.extend(atom(b"rtng", &data_atom(&[value])));
    let mut meta_body = vec![0; 4];
    meta_body.extend(atom(b"ilst", &ilst));
    let mut moov_body = atom(b"free", &[0; 16]);
    moov_body.extend(atom(b"meta", &meta_body));
    let mut result = atom(b"ftyp", &[0; 24]);
    result.extend(atom(b"moov", &moov_body));
    result
}

fn syncsafe(length: usize) -> [u8; 4] {
    [
        (length >> 21) as u8 & 0x7f,
        (length >> 14) as u8 & 0x7f,
        (length >> 7) as u8 & 0x7f,
        length as u8 & 0x7f,
    ]
}

/// ID3v2.4 text frame: encoding byte 3 (UTF-8) followed by the text.
fn id3_text_frame(id: &[u8; 4], text: &[u8]) -> Vec<u8> {
    let mut body = vec![3];
    body.extend(text);
    let mut frame = id.to_vec();
    frame.extend(syncsafe(body.len()));
    frame.extend([0, 0]);
    frame.extend(body);
    frame
}

fn id3_user_text(description: &str, value: &str) -> Vec<u8> {
    let mut text = description.as_bytes().to_vec();
    text.push(0);
    text.extend(value.as_bytes());
    id3_text_frame(b"TXXX", &text)
}

fn mp3_with_frames(frames: &[Vec<u8>]) -> Vec<u8> {
    let body: Vec<u8> = frames.concat();
    let mut result = b"ID3\x04\x00\x00".to_vec();
    result.extend(syncsafe(body.len()));
    result.extend(body);
    result
}

fn flac_with_comments(comments: &[&str]) -> Vec<u8> {
    let mut block = Vec::new();
    let vendor = b"test";
    block.extend((vendor.len() as u32).to_le_bytes());
    block.extend(vendor);
    block.extend((comments.len() as u32).to_le_bytes());
    for comment in comments {
        block.extend((comment.len() as u32).to_le_bytes());
        block.extend(comment.as_bytes());
    }
    let mut result = b"fLaC".to_vec();
    // STREAMINFO (not last), then the VORBIS_COMMENT block (last).
    result.extend([0x00, 0x00, 0x00, 34]);
    result.extend([0; 34]);
    result.push(0x84);
    result.extend(&(block.len() as u32).to_be_bytes()[1..]);
    result.extend(block);
    // The reader expects the first audio frame's sync code after the blocks.
    result.extend([0xff, 0xf8]);
    result
}

fn ogg_page(header_type: u8, packet: &[u8]) -> Vec<u8> {
    let mut segments = vec![255; packet.len() / 255];
    segments.push((packet.len() % 255) as u8);
    let mut page = b"OggS".to_vec();
    page.push(0);
    page.push(header_type);
    page.extend([0; 8]);
    page.extend(1_u32.to_le_bytes());
    page.extend(0_u32.to_le_bytes());
    page.extend([0; 4]);
    page.push(segments.len() as u8);
    page.extend(segments);
    page.extend(packet);
    page
}

fn opus_with_comments(comments: &[&str]) -> Vec<u8> {
    let mut tags = b"OpusTags".to_vec();
    tags.extend(4_u32.to_le_bytes());
    tags.extend(b"test");
    tags.extend((comments.len() as u32).to_le_bytes());
    for comment in comments {
        tags.extend((comment.len() as u32).to_le_bytes());
        tags.extend(comment.as_bytes());
    }
    let mut head = b"OpusHead".to_vec();
    head.extend([1, 2, 0, 0, 0x80, 0xbb, 0, 0, 0, 0, 0]);
    let mut result = ogg_page(2, &head);
    result.extend(ogg_page(0, &tags));
    result
}

fn explicit(data: Vec<u8>, format: &str) -> bool {
    read_audio_tags(&mut Cursor::new(data), format, &|| Ok(()))
        .unwrap()
        .explicit
}

#[test]
fn mp4_rtng_one_and_four_are_explicit_and_two_is_clean() {
    assert!(explicit(mp4_with_rtng(1), "m4a"));
    assert!(explicit(mp4_with_rtng(4), "m4a"));
    assert!(!explicit(mp4_with_rtng(2), "m4a"));
    assert!(!explicit(mp4_with_rtng(0), "m4a"));
}

#[test]
fn id3_txxx_itunesadvisory_and_explicit_read_value_one() {
    let title = id3_text_frame(b"TIT2", b"Title");
    let advisory = id3_user_text("ITUNESADVISORY", "1");
    let explicit_frame = id3_user_text("EXPLICIT", "1");
    let clean = id3_user_text("ITUNESADVISORY", "2");
    assert!(explicit(mp3_with_frames(&[title.clone(), advisory]), "mp3"));
    assert!(explicit(
        mp3_with_frames(&[title.clone(), explicit_frame]),
        "mp3"
    ));
    assert!(!explicit(mp3_with_frames(&[title, clean]), "mp3"));
}

#[test]
fn flac_vorbis_itunesadvisory_explicit_and_rating_read_one_or_explicit() {
    assert!(explicit(
        flac_with_comments(&["TITLE=Title", "ITUNESADVISORY=1"]),
        "flac"
    ));
    assert!(explicit(
        flac_with_comments(&["TITLE=Title", "EXPLICIT=explicit"]),
        "flac"
    ));
    // RATING=1 is a one-star rating in most taggers, not an advisory.
    assert!(!explicit(
        flac_with_comments(&["TITLE=Title", "RATING=1"]),
        "flac"
    ));
    assert!(!explicit(
        flac_with_comments(&["TITLE=Title", "ITUNESADVISORY=2"]),
        "flac"
    ));
}

#[test]
fn opus_vorbis_comments_read_the_advisory() {
    assert!(explicit(
        opus_with_comments(&["TITLE=Title", "ITUNESADVISORY=1"]),
        "opus"
    ));
    assert!(explicit(
        opus_with_comments(&["TITLE=Title", "EXPLICIT=explicit"]),
        "opus"
    ));
    assert!(!explicit(
        opus_with_comments(&["TITLE=Title", "ITUNESADVISORY=0"]),
        "opus"
    ));
}
