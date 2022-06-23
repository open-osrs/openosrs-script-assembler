/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2022, Owain van Brakel <https://github.com/Owain94>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.openosrs.scriptassembler;

import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import static java.lang.Integer.parseInt;
import java.util.stream.Collectors;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.savers.ScriptSaver;
import net.runelite.cache.script.RuneLiteInstructions;
import net.runelite.cache.script.assembler.Assembler;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class ScriptAssembler implements ScriptAssemblerTaskHandler
{
	private static final Logger log = Logging.getLogger(ScriptAssemble.class);

	private final FileTree input;
	private final Directory output;

	public ScriptAssembler(FileTree input, Directory output)
	{
		this.input = input;
		this.output = output;
	}

	@Override
	public void assemble()
	{
		RuneLiteInstructions instructions = new RuneLiteInstructions();
		instructions.init();

		Assembler assembler = new Assembler(instructions);
		ScriptSaver saver = new ScriptSaver();

		int count = 0;
		File scriptOut = new File(output.toString(), Integer.toString(IndexType.CLIENTSCRIPT.getNumber()));
		scriptOut.mkdirs();

		// Clear the target directory to remove stale entries
		try
		{
			MoreFiles.deleteDirectoryContents(scriptOut.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Could not clear scriptOut: " + scriptOut, e);
		}

		for (File scriptFile : input.getFiles()
			.stream()
			.filter((file) -> file.getName().endsWith(".rs2asm"))
			.collect(Collectors.toSet())
		)
		{
			log.info("[INFO] Assembling " + scriptFile);

			try (FileInputStream fin = new FileInputStream(scriptFile))
			{
				ScriptDefinition script = assembler.assemble(fin);
				byte[] packedScript = saver.save(script);

				File targetFile = new File(scriptOut, Integer.toString(script.getId()));
				Files.write(packedScript, targetFile);

				// Copy hash file

				File hashFile = new File(scriptFile.getParentFile().getPath(), Files.getNameWithoutExtension(scriptFile.getName()) + ".hash");
				if (hashFile.exists())
				{
					Files.copy(hashFile, new File(scriptOut, script.getId() + ".hash"));
				}
				else if (script.getId() < 10000) // Scripts >=10000 are RuneLite scripts, so they shouldn't have a .hash
				{
					throw new RuntimeException("Unable to find hash file for " + scriptFile);
				}

				++count;
			}
			catch (IOException ex)
			{
				throw new RuntimeException("unable to open file", ex);
			}
		}

		log.lifecycle("Assembled " + count + " scripts");
	}

	@Override
	public void buildIndex()
	{
		File indexFile = new File(output.toString(), "index");

		try (DataOutputStream fout = new DataOutputStream(new FileOutputStream(indexFile)))
		{
			int indexId = IndexType.CLIENTSCRIPT.getNumber();
			for (File archiveFile : output.getAsFileTree())
			{
				int archiveId;
				try
				{
					archiveId = parseInt(archiveFile.getName());
				}
				catch (NumberFormatException ex)
				{
					continue;
				}

				fout.writeInt(indexId << 16 | archiveId);
			}

			fout.writeInt(-1);
		}
		catch (IOException ex)
		{
			throw new RuntimeException("error building index file", ex);
		}

		log.lifecycle("Index file written");
	}
}
