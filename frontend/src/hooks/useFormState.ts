import { useState } from 'react';

type FormType = 'login' | 'signup' | null;

export function useFormState() {
  const [formType, setFormType] = useState<FormType>(null);
  const [opacity, setOpacity] = useState(1);
  const [formOpacity, setFormOpacity] = useState(0);

  const showForm = (type: FormType) => {
    setOpacity(0);
    setTimeout(() => {
      setFormType(type);
      setFormOpacity(1);
    }, 300);
  };

  const hideForm = () => {
    setFormOpacity(0);
    setTimeout(() => {
      setFormType(null);
      setOpacity(1);
    }, 300);
  };

  return {
    formType,
    opacity,
    formOpacity,
    showForm,
    hideForm,
  };
} 