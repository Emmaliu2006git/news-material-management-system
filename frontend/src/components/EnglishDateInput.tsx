import type { InputHTMLAttributes } from 'react'
import {
  ENGLISH_DATE_PLACEHOLDER,
  ENGLISH_DATE_TIME_PLACEHOLDER,
} from '../dateTime'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'placeholder' | 'type'> & {
  withTime?: boolean
}

export function EnglishDateInput({ withTime = false, ...props }: Props) {
  return (
    <input
      {...props}
      autoComplete={props.autoComplete ?? 'off'}
      placeholder={withTime ? ENGLISH_DATE_TIME_PLACEHOLDER : ENGLISH_DATE_PLACEHOLDER}
      spellCheck={false}
      type="text"
    />
  )
}
