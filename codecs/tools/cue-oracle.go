// SPDX-License-Identifier: GPL-3.0-or-later
// Thin oracle over the pinned MIT WaxFlow package; no parsing logic here.
package main

import (
	"encoding/base64"
	"fmt"
	"github.com/colespringer/waxflow/cue"
	"os"
	"strconv"
)

func row(values ...any) {
	for i, v := range values {
		if i > 0 {
			fmt.Print("\t")
		}
		fmt.Print(base64.StdEncoding.EncodeToString([]byte(fmt.Sprint(v))))
	}
	fmt.Println()
}
func main() {
	b, e := os.ReadFile(os.Args[1])
	if e != nil {
		panic(e)
	}
	rate, _ := strconv.Atoi(os.Args[2])
	total, _ := strconv.ParseInt(os.Args[3], 10, 64)
	s, e := cue.Parse(b)
	if e != nil {
		row("ERROR", e)
		s = cue.ParseTolerant(b)
	} else {
		row("OK")
	}
	row("SHEET", s.Catalog, s.Title, s.Performer)
	for _, r := range s.Rems {
		row("REM", r.Key, r.Value)
	}
	for _, w := range s.Warnings {
		row("WARNING", w.Line, w.Msg)
	}
	for _, f := range s.Files {
		row("FILE", f.Name, f.Type, f.PrecededByData)
		for _, t := range f.Tracks {
			row("TRACK", t.Number, t.Type, t.Title, t.Performer, t.ISRC, t.Pregap, t.Postgap, t.IsAudio())
			for _, ix := range t.Indexes {
				row("INDEX", ix.Number, ix.Frame)
			}
			for _, r := range t.Rems {
				row("TREM", r.Key, r.Value)
			}
			for _, flag := range t.Flags {
				row("FLAG", flag)
			}
		}
		starts, e := f.Starts(rate)
		if e != nil {
			row("STARTERROR", e)
		} else {
			for _, v := range starts {
				row("START", v)
			}
		}
		pieces, e := f.Pieces(rate, total)
		if e != nil {
			row("PIECEERROR", e)
		} else {
			for _, p := range pieces {
				row("PIECE", p.From, p.To, p.Track, p.Audio)
			}
			cuts, skip := cue.Cutpoints(pieces)
			for _, v := range cuts {
				row("PCUT", v)
			}
			for _, v := range skip {
				row("SKIP", v)
			}
		}
		cuts, e := f.Cuts(rate)
		if e != nil {
			row("CUTERROR", e)
		} else {
			for _, v := range cuts {
				row("CUT", v)
			}
		}
	}
	f, e := s.SingleFile()
	if e != nil {
		row("SINGLEERROR", e)
	} else {
		for i := range s.Files {
			if f == &s.Files[i] {
				row("SINGLE", i)
			}
		}
	}
}
