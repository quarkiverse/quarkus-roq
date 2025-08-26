package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;

public class RoqTemplateExtensionTest {
    @Test
    public void testAsStrings() {

        var tags = List.of("tag1", "tag2", "tag3", "tag-four");

        assertThat(RoqTemplateExtension.asStrings("tag1 tag2 tag3 tag-four"))
                .containsExactlyElementsOf(tags);

        assertThat(RoqTemplateExtension.asStrings("tag1,tag2,tag3,tag-four"))
                .containsExactlyElementsOf(tags);

        assertThat(RoqTemplateExtension.asStrings("tag1, tag2, tag3, tag-four"))
                .containsExactlyElementsOf(tags);

        assertThat(RoqTemplateExtension.asStrings("tag1;tag2;tag3;tag-four"))
                .containsExactlyElementsOf(tags);

        assertThat(RoqTemplateExtension.asStrings("tag1; tag2; tag3; tag-four"))
                .containsExactlyElementsOf(tags);

        assertThat(RoqTemplateExtension.asStrings("tag1\ttag2\ttag3\ttag-four"))
                .containsExactlyElementsOf(tags);
    }

    @Test
    void testContentAbstract() {
        assertThat(RoqTemplateExtension.contentAbstract(
                """
                        <div id="Content">
                        <div id="bannerL"><!-- Tag ID: lipsumcom_left_siderail -->
                        <div align="center" data-freestar-ad="__300x600" id="lipsumcom_left_siderail" name="lipsumcom_left_siderail" data-google-query-id="CPve8ZyDqI8DFXVFHQkd09UQow">

                        <div id="google_ads_iframe_/15184186,22440292294/lipsumcom_left_siderail_0__container__" style="border: 0pt none;"><iframe id="google_ads_iframe_/15184186,22440292294/lipsumcom_left_siderail_0" name="google_ads_iframe_/15184186,22440292294/lipsumcom_left_siderail_0" title="3rd party ad content" width="300" height="600" scrolling="no" marginwidth="0" marginheight="0" frameborder="0" aria-label="Advertisement" tabindex="0" allow="private-state-token-redemption;attribution-reporting" data-load-complete="true" data-google-container-id="2" style="border: 0px; vertical-align: bottom;"></iframe><div class="__fs-ancillary" style="visibility: visible;"><div class="__fs-branding"><a href="https://ads.freestar.com/?utm_campaign=branding&amp;utm_medium=display&amp;utm_source=lipsum.com&amp;utm_content=lipsumcom_left_siderail" target="_blank" rel="noreferrer"><img src="https://a.pub.network/core/imgs/fslogo-green.svg" alt="freestar" width="14" height="14"></a></div><div class="fs-branding-spacer"></div></div></div></div></div>
                        <div id="bannerR"><!-- Tag ID: lipsumcom_right_siderail -->
                        <div align="center" data-freestar-ad="__300x600" id="lipsumcom_right_siderail" name="lipsumcom_right_siderail" data-google-query-id="CO3k9pyDqI8DFbBeHQkd_Wcu6g">

                        <div id="_fs-ad-iframe-container"><iframe id="_fs-ad-20694175f305fd4e8" width="300" height="600" frameborder="0" marginheight="0" marginwidth="0" scrolling="no"></iframe><div class="__fs-ancillary" style="visibility: visible;"><div class="__fs-branding"><a href="https://ads.freestar.com/?utm_campaign=branding&amp;utm_medium=display&amp;utm_source=lipsum.com&amp;utm_content=lipsumcom_right_siderail" target="_blank" rel="noreferrer"><img src="https://a.pub.network/core/imgs/fslogo-green.svg" alt="freestar" width="14" height="14"></a></div><div class="fs-branding-spacer"></div></div></div></div></div>
                        <div id="Panes"><div>
                        <h2>What is Lorem Ipsum?</h2>
                        <p><strong>Lorem Ipsum</strong> is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.</p>
                        </div><div>
                        <h2>Why do we use it?</h2>
                        <p>It is a long established fact that a reader will be distracted by the readable content of a page when looking at its layout. The point of using Lorem Ipsum is that it has a more-or-less normal distribution of letters, as opposed to using 'Content here, content here', making it look like readable English. Many desktop publishing packages and web page editors now use Lorem Ipsum as their default model text, and a search for 'lorem ipsum' will uncover many web sites still in their infancy. Various versions have evolved over the years, sometimes by accident, sometimes on purpose (injected humour and the like).</p>
                        </div><br><div>
                        <h2>Where does it come from?</h2>
                        <p>Contrary to popular belief, Lorem Ipsum is not simply random text. It has roots in a piece of classical Latin literature from 45 BC, making it over 2000 years old. Richard McClintock, a Latin professor at Hampden-Sydney College in Virginia, looked up one of the more obscure Latin words, consectetur, from a Lorem Ipsum passage, and going through the cites of the word in classical literature, discovered the undoubtable source. Lorem Ipsum comes from sections 1.10.32 and 1.10.33 of "de Finibus Bonorum et Malorum" (The Extremes of Good and Evil) by Cicero, written in 45 BC. This book is a treatise on the theory of ethics, very popular during the Renaissance. The first line of Lorem Ipsum, "Lorem ipsum dolor sit amet..", comes from a line in section 1.10.32.</p><p>The standard chunk of Lorem Ipsum used since the 1500s is reproduced below for those interested. Sections 1.10.32 and 1.10.33 from "de Finibus Bonorum et Malorum" by Cicero are also reproduced in their exact original form, accompanied by English versions from the 1914 translation by H. Rackham.</p>
                        </div><div>
                        <h2>Where can I get some?</h2>
                        <p>There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour, or randomised words which don't look even slightly believable. If you are going to use a passage of Lorem Ipsum, you need to be sure there isn't anything embarrassing hidden in the middle of text. All the Lorem Ipsum generators on the Internet tend to repeat predefined chunks as necessary, making this the first true generator on the Internet. It uses a dictionary of over 200 Latin words, combined with a handful of model sentence structures, to generate Lorem Ipsum which looks reasonable. The generated Lorem Ipsum is therefore always free from repetition, injected humour, or non-characteristic words etc.</p>
                        <form method="post" action="/feed/html"><table style="width:100%"><tbody><tr><td rowspan="2"><input type="text" name="amount" value="5" size="3" id="amount"></td><td rowspan="2"><table style="text-align:left"><tbody><tr><td style="width:20px"><input type="radio" name="what" value="paras" id="paras" checked="checked"></td><td><label for="paras">paragraphs</label></td></tr><tr><td style="width:20px"><input type="radio" name="what" value="words" id="words"></td><td><label for="words">words</label></td></tr><tr><td style="width:20px"><input type="radio" name="what" value="bytes" id="bytes"></td><td><label for="bytes">bytes</label></td></tr><tr><td style="width:20px"><input type="radio" name="what" value="lists" id="lists"></td><td><label for="lists">lists</label></td></tr></tbody></table></td><td style="width:20px"><input type="checkbox" name="start" id="start" value="yes" checked="checked"></td><td style="text-align:left"><label for="start">Start with 'Lorem<br>ipsum dolor sit amet...'</label></td></tr><tr><td></td><td style="text-align:left"><input type="submit" name="generate" id="generate" value="Generate Lorem Ipsum"></td></tr></tbody></table></form></div><br></div>
                        <hr><div class="boxed"><strong>Donate:</strong> If you use this site regularly and would like to help keep the site on the Internet, please consider donating a small sum to help pay for the hosting and bandwidth bill. There is no minimum donation, any sum is appreciated - click <a target="_blank" href="/donate" class="lnk">here</a> to donate using PayPal. Thank you for your support. Donate bitcoin: 16UQLq1HZ3CNwhvgrarV6pMoA2CDjb4tyF</div>

                        <hr><div class="boxed"><strong>Translations:</strong> Can you help translate this site into a foreign language ? Please email us with details if you can help.</div>

                        <hr><div class="boxed">There is a set of mock banners available <a href="/banners" class="lnk">here</a> in three colours and in a range of standard banner sizes:<br><a href="/banners"><img src="/images/banners/black_234x60.gif" width="234" height="60" alt="Banners"></a><a href="/banners"><img src="/images/banners/grey_234x60.gif" width="234" height="60" alt="Banners"></a><a href="/banners"><img src="/images/banners/white_234x60.gif" width="234" height="60" alt="Banners"></a></div>

                        <hr><div class="boxed" id="Packages">
                        <a target="_blank" rel="noopener" href="https://github.com/traviskaufman/node-lipsum">NodeJS</a>
                        <a target="_blank" rel="noopener" href="https://code.google.com/p/pypsum/">Python Interface</a>
                        <a target="_blank" rel="noopener" href="https://gtklipsum.sourceforge.net/">GTK Lipsum</a>
                        <a target="_blank" rel="noopener" href="https://github.com/gsavage/lorem_ipsum/tree/master">Rails</a>
                        <a target="_blank" rel="noopener" href="https://github.com/cerkit/LoremIpsum/">.NET</a>
                        </div>

                        <hr><div style="margin:10px 0"><!-- Tag ID: lipsumcom_incontent -->
                        <div align="center" data-freestar-ad="__336x280 __728x90 __640x360" id="lipsumcom_incontent" name="lipsumcom_incontent" data-google-query-id="CMCXr-aCqI8DFVt_HQkd7IcHng">
                          <script data-cfasync="false" type="text/javascript">
                            freestar.config.enabled_slots.push({ placementName: "lipsumcom_incontent", slotId: "lipsumcom_incontent" });
                          </script>
                        <div id="_fs-ad-iframe-container"><iframe id="_fs-ad-221fbe7cc3f085dd" width="728" height="90" frameborder="0" marginheight="0" marginwidth="0" scrolling="no"></iframe><div class="__fs-ancillary" style="visibility: visible;"><div class="__fs-branding"><a href="https://ads.freestar.com/?utm_campaign=branding&amp;utm_medium=display&amp;utm_source=lipsum.com&amp;utm_content=lipsumcom_incontent" target="_blank" rel="noreferrer"><img src="https://a.pub.network/core/imgs/fslogo-green.svg" alt="freestar" width="14" height="14"></a></div><div class="fs-branding-spacer"></div></div></div></div></div>
                        <hr><div id="Translation">

                        <h3>The standard Lorem Ipsum passage, used since the 1500s</h3><p>"Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."</p><h3>Section 1.10.32 of "de Finibus Bonorum et Malorum", written by Cicero in 45 BC</h3><p>"Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?"</p>
                        <h3>1914 translation by H. Rackham</h3>
                        <p>"But I must explain to you how all this mistaken idea of denouncing pleasure and praising pain was born and I will give you a complete account of the system, and expound the actual teachings of the great explorer of the truth, the master-builder of human happiness. No one rejects, dislikes, or avoids pleasure itself, because it is pleasure, but because those who do not know how to pursue pleasure rationally encounter consequences that are extremely painful. Nor again is there anyone who loves or pursues or desires to obtain pain of itself, because it is pain, but because occasionally circumstances occur in which toil and pain can procure him some great pleasure. To take a trivial example, which of us ever undertakes laborious physical exercise, except to obtain some advantage from it? But who has any right to find fault with a man who chooses to enjoy a pleasure that has no annoying consequences, or one who avoids a pain that produces no resultant pleasure?"</p>
                        <h3>Section 1.10.33 of "de Finibus Bonorum et Malorum", written by Cicero in 45 BC</h3>
                        <p>"At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat."</p>
                        <h3>1914 translation by H. Rackham</h3>
                        <p>"On the other hand, we denounce with righteous indignation and dislike men who are so beguiled and demoralized by the charms of pleasure of the moment, so blinded by desire, that they cannot foresee the pain and trouble that are bound to ensue; and equal blame belongs to those who fail in their duty through weakness of will, which is the same as saying through shrinking from toil and pain. These cases are perfectly simple and easy to distinguish. In a free hour, when our power of choice is untrammelled and when nothing prevents our being able to do what we like best, every pleasure is to be welcomed and every pain avoided. But in certain circumstances and owing to the claims of duty or the obligations of business it will frequently occur that pleasures have to be repudiated and annoyances accepted. The wise man therefore always holds in these matters to this principle of selection: he rejects pleasures to secure other greater pleasures, or else he endures pains to avoid worse pains."</p>
                        </div>
                        </div>
                        """,
                75)).isEqualTo(
                        """
                                What is Lorem Ipsum?
                                Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of...""");
    }
}
