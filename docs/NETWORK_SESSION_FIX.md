# 0.5.1 校园网登录态修复

此前版本首次连接后可以查询，但业务登录态失效时，学校套餐地址可能直接返回 HTTP 500，而不是跳转统一认证。客户端只识别 401 和认证页，导致没有启动恢复流程。旧版 WebView 导入还会人工改变 Cookie 路径，新业务会话和旧值可能同时发送。

新版先复用现有登录。遇到疑似认证失效的响应时，访问学校官方门户入口，使用已保存的对应站点 Cookie 尝试正常 SSO 跳转，并只重试套餐查询一次。跳转仅允许已支持的学校 HTTPS 站点，有次数上限。请求复用 WebView 认证时的 User-Agent，保持两个客户端的会话环境一致。

如果学校明确返回登录页，则保留原 scope 和缓存，标记需要重新认证并打开官方连接页。取消登录后不会因返回前台立即再次弹出；下次主动更新可重新连接。如果门户入口正常而业务仍返回 500，则展示业务服务故障，保留原记录，不循环尝试登录。

Cookie schema 升为 2。旧版 JSESSIONID 和 CASTGC 的人工尾斜线路径迁移到对应业务路径，已确认的负载均衡及路由 Cookie 恢复根路径。真实服务器条目优先于人工路径条目；更新和删除不会保留旧版路径下的同名值。新版完整属性快照继续保留合法的不同路径 Cookie，避免将两个学校域名的 JSESSIONID 混为一谈。

学校登录页优先使用 AndroidX WebKit 的 `CookieManagerCompat.getCookieInfo()` 获取完整属性，并在不支持的 WebView 提供方上回退到关键 Cookie 的已知路径，不编造 Expires 或 Max-Age。进入 WebView 前也会迁移旧快照，覆盖升级后直接打开“连接校园网”的场景。API 依据见 [Android CookieManagerCompat 文档](https://developer.android.com/reference/kotlin/androidx/webkit/CookieManagerCompat)。

这些变化通过纯虚构账户和本地 TLS 服务器回归验证，不把真实登录凭据写入测试或文档。它们不能改变学校会话的实际有效期；统一认证已失效或需要验证时仍由本人完成官方认证。实际手机长时间空闲后的有效期及全部认证策略仍需持续观察。
