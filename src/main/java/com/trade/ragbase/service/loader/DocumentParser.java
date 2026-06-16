package com.trade.ragbase.service.loader;

import java.io.InputStream;

public interface DocumentParser {

    String supportedType();

    ParseResult parse(InputStream inputStream, String fileName);
}
