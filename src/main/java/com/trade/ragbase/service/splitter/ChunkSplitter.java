package com.trade.ragbase.service.splitter;

import com.trade.ragbase.service.loader.ParseResult;

import java.util.List;

public interface ChunkSplitter {

    List<ChunkResult> split(ParseResult parseResult, ChunkConfig config);
}
