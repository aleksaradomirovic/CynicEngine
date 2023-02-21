package cynic.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AssetLoader {
	public static Entry parseFile(Path f) throws IOException {
		AwareReader reader = new AwareReader(f.toString(), f.toUri().toURL().openStream());
		Entry e = new Entry(null, reader);
		reader.close();
		return e;
	}
	
	public static class Entry implements Iterable<Entry> {
		public final String type;
		
		private final Map<String,Object> primitives;
		private final List<Entry> subentries;
		
		@SuppressWarnings("unchecked")
		public <T> Optional<T> getPrimitive(String id, Class<T> typeName) {
			Object t = primitives.get(id);
			if(t != null && typeName.isAssignableFrom(t.getClass()))
				return Optional.of((T)t);
			return Optional.empty();
		}
		
		protected Entry(String type, Reader in) throws IOException {
			this.primitives = new HashMap<>();
			this.subentries = new LinkedList<>();
			this.type = type;
			
			try {
				StringBuilder buf = new StringBuilder(1024);
				String key = null;
				int c, mode = 0;
				while(mode >= 0) {
					c = in.read();
					
					if(c == '#') while(c != '\n' && c >= 0)
						c = in.read(); // comments
					
					switch(mode) {
						case 1: // hit a separator
							if(c == '=') {
								key = buf.toString().toLowerCase();
								buf = new StringBuilder(1024);
								mode = 2;
								break;
							} else if(c > ' ') {
								throw new ParserException(in, "Can't have delimiters in a type declaration!");
							}
							break;
						case 0: // reading entry name
							if(c <= ' ') {
								if(buf.length() > 0) {
									mode = 1;
								}
							} else if(c == '}') {
								c = -1;
							} else if(c == '"' || c == '\'' || c == '{') {
								throw new ParserException(in, "Illegal character type in declaration!");
							} else if(c == '=') {
								key = buf.toString().toLowerCase();
								buf = new StringBuilder(1024);
								mode = 2;
								break;
							} else if(c == '}') {
								c = -1;
							} else {
								buf.append((char)c);
							}
							break;
						case 2: // declaring type
							if(c <= ' ') {
								if(buf.length() > 0) {
									String out = buf.toString().toLowerCase();
									Object val = out;
									
									try {
										val = Double.parseDouble(out);
//										val = Long.parseLong(out);
//										val = Integer.parseInt(out);
									} catch(NumberFormatException e) {}
									
									if(val == out) {
										switch(out) {
											case "true":
											case "yes":
												val = Boolean.TRUE;
												break;
											case "false":
											case "no":
												val = Boolean.FALSE;
												break;
										}
									}
									
									primitives.put(key, val);
									mode = 4;
								}
							} else if(c == '"') {
								if(buf.length() == 0)
									mode = 3;
								else 
									throw new ParserException(in, "Can't have quotations in the middle of a declaration!");
							} else if(c == '{') {
								if(buf.length() == 0) {
									Entry e = new Entry(key, in);
									subentries.add(e);
									primitives.put(key,e);
									mode = 4;
								} else 
									throw new ParserException(in, "Can't have braces in the middle of a declaration!");
							} else if(c == '[') {
								primitives.put(key, new ListBuilder(in).out);
								mode = 4;
							} else if(c == '=' || c == '}' || c == '\'') {
								throw new ParserException(in, "Illegal character type in declaration!");
							} else {
								buf.append((char)c);
							}
							break;
						case 3: // declaring string type explicitly
							if(c == '"') {
								primitives.put(key, buf.toString());
								mode = 4;
							} else if(c == '\\') {
								if((c = in.read()) < 0) break; // go straight to exception
								buf.append((char)c);
							} else {
								buf.append((char)c);
							}
							break;
						case 4: // hit quotes end
							if(c == '}') {
								c = -1;
							} else if(c <= ' ') {
								buf = new StringBuilder(1024);
								mode = 0;
							} else {
								throw new ParserException(in, "Can't have non-delimiters in a string or numeral declaration!");
							}
							break;
					}
					
					if(c < 0) {
						if(mode != 4 && mode != 0)
							throw new ParserException(in, "Illegal ending to file!");
						break;
					}
				}
			} catch(Exception e) {
				if(e instanceof ParserException) throw e;
				throw new ParserException(in, e.getMessage());
			}
			
		}
		
		private static class ListBuilder {
			public final List<String> out;
			
			public ListBuilder(Reader in) throws IOException {
				out = new LinkedList<>();
				
				int c, mode = 0;
				StringBuilder buf = new StringBuilder(1024);
				while(mode >= 0) {
					c = in.read();
					switch(mode) {
						case 0: // start
							if(c == '"') {
								if(buf.length() > 0)
									throw new ParserException(in, "Can't have quotations in the middle of a declaration!");
								else
									mode = 1;
							} else if(c == ']') {
								if(buf.length() > 0) {
									out.add(buf.toString().toLowerCase());
								}
								mode = -1;
							} else if(c <= ' ') {
								if(buf.length() > 0) {
									out.add(buf.toString().toLowerCase());
									buf = new StringBuilder(1024);
								}
							} else {
								buf.append((char)c);
							}
							break;
						case 1: // quoted string
							if(c == '"') {
								out.add(buf.toString());
								mode = 2;
							} else if(c == '\\') {
								if((c = in.read()) < 0) break; // go straight to exception
								buf.append((char)c);
							} else {
								buf.append((char)c);
							}
							break;
						case 2:
							if(c == ']') {
								mode = -1;
							} else if(c <= ' ') {
								buf = new StringBuilder(1024);
								mode = 0;
							} else {
								throw new ParserException(in, "Can't have non-delimiters in a string or numeral declaration!");
							}
							break;
					}
					
					if(c == -1)
						throw new ParserException(in, "Illegal ending to file!");
				}
			}
		}
		
		public static class ParserException extends IOException {
			private static final long serialVersionUID = 2445088536348395135L;

			public ParserException(Reader in, String msg) {
				super("["+in+"]: "+msg);
			}
		}
		
		@Override
		public String toString() {
			return primitives+" "+subentries;
		}

		@Override
		public Iterator<Entry> iterator() {
			return subentries.iterator();
		}
	}
	
	public static class AwareReader extends Reader {
		private final Reader in;
		private final String file;
		
		private int pos;
		
		public AwareReader(String file, InputStream is) throws IOException {
			this.file = file;
			this.in = new InputStreamReader(is);
		}
		
		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			for(len+=off;off<len;off++) {
				cbuf[off] = (char) read();
			}
			return 0;
		}
		
		@Override
		public int read() throws IOException {
			pos++;
			return in.read();
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
		
		@Override
		public String toString() {
			return file+": "+pos;
		}
	}
}
