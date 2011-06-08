import java.math.BigDecimal;

import javax.xml.ws.BindingProvider;

import com.predic8.common._1.CurrencyType;
import com.predic8.common._1.MoneyType;
import com.predic8.material._1.ArticleType;
import com.predic8.wsdl.material.articleservice._1.ArticleService;
import com.predic8.wsdl.material.articleservice._1.ArticleServicePT;


public class ArticleClient {

	public static void main(String[] args) {
		ArticleService service = new ArticleService();
		ArticleServicePT port = service.getArticleServicePTPort();
		
		((BindingProvider)port).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
		"http://localhost:2000/material/ArticleService");

		
		ArticleType aType = new ArticleType();
		
		/**
		 * The correct pattern of the article ID is
		 * 				\w\w - \d{3}
		 * Here we intentionally make an invalid input
		 */
		aType.setId("123");
		//aType.setId("EX-123");  //Correct ID for article type
		
		aType.setDescription("Descr");
		aType.setName("A_Name");
		MoneyType money = new MoneyType();
		money.setAmount(new BigDecimal(456));
		money.setCurrency(CurrencyType.USD);
		aType.setPrice(money);
		
		try {
			port.create(aType);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
