package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.lang.*;

public interface Polyglot {

    ExchangeExpression.Language getLanguage();

    void setLanguage(ExchangeExpression.Language language);
}
