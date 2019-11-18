/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

/**
 * 此类主要用来查找占位符，比如：abc${xxx:123}def，将 "xxx:123" 查找出来,
 * 然后将 xxx 的替换交由 TokenHandler 处理。 
 * @author Clinton Begin
 */
public class GenericTokenParser {

	private final String openToken; // 占位符开始标记
	private final String closeToken; // 占位符结束标记
	private final TokenHandler handler;

	public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
		this.openToken = openToken;
		this.closeToken = closeToken;
		this.handler = handler;
	}
	
	public String parse(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		// 搜索 "${" 的第一次出现的位置
		int start = text.indexOf(openToken, 0);
		if (start == -1) {
			return text;
		}
		char[] src = text.toCharArray();
		int offset = 0;
		// 用来记录解析之后的字符串
		final StringBuilder builder = new StringBuilder();
		StringBuilder expression = null;
		while (start > -1) {
			if (start > 0 && src[start - 1] == '\\') {
				// 如果是 "\${.." 这种转义形式，则直接将字符追加上，但是要去掉转义的 "\"
				// “start - offset - 1”，其中，"- offset"表示去掉以前处理的字符，"- 1" 表示去掉"\"的长度.
				builder.append(src, offset, start - offset - 1).append(openToken);
				// 将位移推进到 转义字符 "${" 之后
				offset = start + openToken.length();
			} else {
				// 查找到开始标记，且不是转义的
				if (expression == null) {
					expression = new StringBuilder();
				} else {
					expression.setLength(0);
				}
				// 将转义字符前边的字符串追加上
				// 比如："abc${xx}dfg"格式，查找到"${"之后，还需要将前边的"abc"追加上.
				builder.append(src, offset, start - offset);
				offset = start + openToken.length();
				int end = text.indexOf(closeToken, offset);
				while (end > -1) {
					if (end > offset && src[end - 1] == '\\') {
						// 如果是转义字符，仍然去掉"\"，然后直接追加
						expression.append(src, offset, end - offset - 1).append(closeToken);
						offset = end + closeToken.length();
						end = text.indexOf(closeToken, offset);
					} else {
						// 拼接上表达式
						expression.append(src, offset, end - offset);
						offset = end + closeToken.length();
						break;
					}
				}
				if (end == -1) {
					// 未发现，则直接加上
					builder.append(src, start, src.length - start);
					offset = src.length;
				} else {
					// 使用 handler 处理表达式
					builder.append(handler.handleToken(expression.toString()));
					offset = end + closeToken.length();
				}
			}
			// 查找下一个 开始标签
			start = text.indexOf(openToken, offset);
		}
		if (offset < src.length) {
			builder.append(src, offset, src.length - offset);
		}
		return builder.toString();
	}
}
